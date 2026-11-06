package dev.caiobento.pix.pagamento;

/** Por onde ficamos sabendo do Pix. */
public enum OrigemPagamento {
	WEBHOOK,
	/** A rotina de conciliação consultou o PSP e achou o pagamento que o webhook não trouxe. */
	CONCILIACAO
}
