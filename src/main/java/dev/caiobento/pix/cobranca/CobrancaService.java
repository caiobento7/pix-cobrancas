package dev.caiobento.pix.cobranca;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.caiobento.pix.config.PixProperties;
import dev.caiobento.pix.psp.PspClient;

@Service
public class CobrancaService {

	private final CobrancaRepository cobrancas;
	private final DevolucaoRepository devolucoes;
	private final PspClient psp;
	private final Clock relogio;
	private final PixProperties propriedades;

	public CobrancaService(CobrancaRepository cobrancas, DevolucaoRepository devolucoes, PspClient psp, Clock relogio,
			PixProperties propriedades) {
		this.cobrancas = cobrancas;
		this.devolucoes = devolucoes;
		this.psp = psp;
		this.relogio = relogio;
		this.propriedades = propriedades;
	}

	@Transactional
	public CobrancaResposta criar(BigDecimal valor, String descricao, Duration expiracao) {
		Duration prazo = expiracao != null ? expiracao : propriedades.expiracaoPadrao();
		// txid: de 26 a 35 caracteres alfanuméricos, segundo a API Pix. Um UUID sem hífens tem 32.
		String txid = UUID.randomUUID().toString().replace("-", "");
		String copiaECola = psp.criarCobranca(txid, valor, prazo, descricao);
		Instant agora = relogio.instant();
		Cobranca cobranca = new Cobranca(txid, valor, descricao, copiaECola, agora, agora.plus(prazo));
		return CobrancaResposta.de(cobrancas.save(cobranca));
	}

	@Transactional(readOnly = true)
	public CobrancaResposta buscar(String txid) {
		return CobrancaResposta.de(carregar(txid));
	}

	/**
	 * Devolve parte ou todo o valor pago. Pedir de novo com o mesmo id e o mesmo valor não devolve
	 * outra vez: só retorna a devolução que já existe.
	 */
	@Transactional
	public ResultadoDevolucao devolver(String txid, String idDevolucao, BigDecimal valor, String motivo) {
		var existente = devolucoes.findById(idDevolucao);
		if (existente.isPresent()) {
			Devolucao devolucao = existente.get();
			boolean mesmoPedido = devolucao.getCobranca().getTxid().equals(txid)
					&& devolucao.getValor().compareTo(valor) == 0;
			if (!mesmoPedido) {
				throw new ConflitoException("O id de devolução " + idDevolucao + " já foi usado com outros dados.");
			}
			return new ResultadoDevolucao(DevolucaoResposta.de(devolucao), false);
		}

		Cobranca cobranca = carregar(txid);
		Devolucao devolucao = cobranca.registrarDevolucao(idDevolucao, valor, motivo, relogio.instant());
		psp.solicitarDevolucao(cobranca.getEndToEndId(), idDevolucao, valor);
		cobrancas.saveAndFlush(cobranca);
		return new ResultadoDevolucao(DevolucaoResposta.de(devolucao), true);
	}

	/** @return true se a cobrança estava ativa e venceu agora. */
	@Transactional
	public boolean expirarSeVencida(String txid) {
		return carregar(txid).expirarSeVencida(relogio.instant());
	}

	private Cobranca carregar(String txid) {
		return cobrancas.findByTxid(txid).orElseThrow(() -> new CobrancaNaoEncontradaException(txid));
	}

	public record ResultadoDevolucao(DevolucaoResposta devolucao, boolean criada) {
	}
}
