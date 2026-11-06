package dev.caiobento.pix.conciliacao;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dev.caiobento.pix.cobranca.Cobranca;
import dev.caiobento.pix.cobranca.CobrancaRepository;
import dev.caiobento.pix.cobranca.CobrancaService;
import dev.caiobento.pix.cobranca.StatusCobranca;
import dev.caiobento.pix.config.PixProperties;
import dev.caiobento.pix.pagamento.OrigemPagamento;
import dev.caiobento.pix.pagamento.ProcessadorDePagamentos;
import dev.caiobento.pix.pagamento.ResultadoPagamento;
import dev.caiobento.pix.psp.PspClient;

/**
 * Rede de segurança do webhook: de tempos em tempos consulta no PSP as cobranças que continuam
 * ATIVAS. Se o webhook se perdeu, o pagamento é achado aqui; se a cobrança venceu, ela é expirada.
 * Os dois caminhos usam o mesmo {@link ProcessadorDePagamentos}, então o Pix nunca é contado duas vezes.
 */
@Component
public class Conciliacao {

	private static final Logger log = LoggerFactory.getLogger(Conciliacao.class);

	private final CobrancaRepository cobrancas;
	private final CobrancaService servico;
	private final ProcessadorDePagamentos processador;
	private final PspClient psp;
	private final Clock relogio;
	private final PixProperties propriedades;

	public Conciliacao(CobrancaRepository cobrancas, CobrancaService servico, ProcessadorDePagamentos processador,
			PspClient psp, Clock relogio, PixProperties propriedades) {
		this.cobrancas = cobrancas;
		this.servico = servico;
		this.processador = processador;
		this.psp = psp;
		this.relogio = relogio;
		this.propriedades = propriedades;
	}

	@Scheduled(fixedDelayString = "${pix.conciliacao.intervalo}", initialDelayString = "${pix.conciliacao.intervalo}")
	void agendada() {
		executar();
	}

	public Resumo executar() {
		Instant agora = relogio.instant();
		Instant limite = agora.minus(propriedades.conciliacao().idadeMinima());
		List<Cobranca> pendentes = cobrancas.findTop100ByStatusAndCriadaEmBeforeOrderByCriadaEmAsc(
				StatusCobranca.ATIVA, limite);

		int pagas = 0;
		int expiradas = 0;
		int falhas = 0;
		for (Cobranca cobranca : pendentes) {
			try {
				var consulta = psp.consultarCobranca(cobranca.getTxid());
				if (consulta.paga()) {
					var resultado = processador.processar(consulta.pagamento(), OrigemPagamento.CONCILIACAO);
					if (resultado == ResultadoPagamento.APLICADO) {
						pagas++;
					}
				}
				else if (servico.expirarSeVencida(cobranca.getTxid())) {
					expiradas++;
				}
			}
			catch (RuntimeException erro) {
				// Uma cobrança com problema não pode travar as outras; ela volta na próxima rodada.
				falhas++;
				log.warn("Conciliação da cobrança {} falhou: {}", cobranca.getTxid(), erro.toString());
			}
		}
		Resumo resumo = new Resumo(pendentes.size(), pagas, expiradas, falhas);
		if (resumo.consultadas() > 0) {
			log.info("Conciliação: {}", resumo);
		}
		return resumo;
	}

	public record Resumo(int consultadas, int pagas, int expiradas, int falhas) {
	}
}
