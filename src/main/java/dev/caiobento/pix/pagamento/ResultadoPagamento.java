package dev.caiobento.pix.pagamento;

/** O que aconteceu com um Pix recebido. Fica gravado junto com o pagamento, para auditoria. */
public enum ResultadoPagamento {
	/** Baixou a cobrança. */
	APLICADO,
	/** Esse endToEndId já tinha sido processado (webhook repetido ou já visto na conciliação). */
	DUPLICADO,
	/** Nenhuma cobrança com esse txid: Pix avulso ou de outro sistema. */
	COBRANCA_DESCONHECIDA,
	/** O valor pago não bate com o da cobrança: a cobrança continua aberta e o caso vai para análise. */
	VALOR_DIVERGENTE,
	/** A cobrança já tinha sido paga por outro Pix: o dinheiro precisa ser devolvido. */
	COBRANCA_JA_PAGA
}
