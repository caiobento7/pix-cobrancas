package dev.caiobento.pix.cobranca;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Valores saem como texto com duas casas ("25.90"), no mesmo formato da API Pix. */
public record CobrancaResposta(
		String txid,
		String valor,
		String descricao,
		StatusCobranca status,
		String pixCopiaECola,
		Instant criadaEm,
		Instant expiraEm,
		Pagamento pagamento,
		List<DevolucaoResposta> devolucoes) {

	public record Pagamento(String endToEndId, String valor, Instant horario) {
	}

	static CobrancaResposta de(Cobranca c) {
		Pagamento pagamento = c.getEndToEndId() == null ? null
				: new Pagamento(c.getEndToEndId(), texto(c.getValorPago()), c.getPagaEm());
		return new CobrancaResposta(c.getTxid(), texto(c.getValor()), c.getDescricao(), c.getStatus(),
				c.getPixCopiaECola(), c.getCriadaEm(), c.getExpiraEm(), pagamento,
				c.getDevolucoes().stream().map(DevolucaoResposta::de).toList());
	}

	static String texto(BigDecimal valor) {
		return valor.setScale(2).toPlainString();
	}
}
