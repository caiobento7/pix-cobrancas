package dev.caiobento.pix.cobranca;

public enum StatusCobranca {
	/** Criada e esperando pagamento. */
	ATIVA,
	PAGA,
	/** Venceu sem pagamento. */
	EXPIRADA,
	/** Todo o valor pago foi devolvido. */
	DEVOLVIDA
}
