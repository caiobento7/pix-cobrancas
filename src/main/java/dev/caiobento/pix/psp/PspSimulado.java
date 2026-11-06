package dev.caiobento.pix.psp;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Component;

import dev.caiobento.pix.pagamento.PixRecebido;

/**
 * PSP em memória, para rodar e testar sem conta em banco. Em produção, outra implementação de
 * {@link PspClient} faria as chamadas HTTP (com mTLS e OAuth2) para a API Pix do banco.
 */
@Component
public class PspSimulado implements PspClient {

	private static final DateTimeFormatter DATA_E2E = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
		.withZone(ZoneOffset.UTC);

	private static final String ALFANUMERICO = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

	private final Clock relogio;
	private final Map<String, BigDecimal> cobrancas = new ConcurrentHashMap<>();
	private final Map<String, PixRecebido> pagamentos = new ConcurrentHashMap<>();
	private final Map<String, BigDecimal> devolucoes = new ConcurrentHashMap<>();

	public PspSimulado(Clock relogio) {
		this.relogio = relogio;
	}

	@Override
	public String criarCobranca(String txid, BigDecimal valor, Duration expiracao, String descricao) {
		cobrancas.put(txid, valor);
		return "00020101021226830014br.gov.bcb.pix2561pix.simulado.local/qr/v2/" + txid
				+ "5204000053039865802BR5911CAIO BENTO6014RIO DE JANEIRO62070503***6304SIMU";
	}

	@Override
	public ConsultaCobranca consultarCobranca(String txid) {
		return new ConsultaCobranca(txid, pagamentos.get(txid));
	}

	@Override
	public void solicitarDevolucao(String endToEndId, String idDevolucao, BigDecimal valor) {
		devolucoes.putIfAbsent(idDevolucao, valor);
	}

	/** Simula alguém pagando a cobrança no app do banco. Devolve o Pix que o PSP mandaria no webhook. */
	public PixRecebido simularPagamento(String txid) {
		BigDecimal valor = cobrancas.get(txid);
		if (valor == null) {
			throw new IllegalArgumentException("O PSP não conhece a cobrança " + txid);
		}
		return pagamentos.computeIfAbsent(txid,
				t -> new PixRecebido(novoEndToEndId(), t, valor, relogio.instant()));
	}

	public Map<String, BigDecimal> devolucoesSolicitadas() {
		return Map.copyOf(devolucoes);
	}

	/** Formato do SPI: "E" + ISPB (8) + data e hora (yyyyMMddHHmm) + 11 caracteres. */
	private String novoEndToEndId() {
		StringBuilder sufixo = new StringBuilder();
		ThreadLocalRandom aleatorio = ThreadLocalRandom.current();
		for (int i = 0; i < 11; i++) {
			sufixo.append(ALFANUMERICO.charAt(aleatorio.nextInt(ALFANUMERICO.length())));
		}
		return "E00000000" + DATA_E2E.format(relogio.instant()) + sufixo;
	}
}
