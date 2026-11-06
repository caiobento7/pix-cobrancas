package dev.caiobento.pix.cobranca;

/** O pedido conflita com algo que já existe (vira HTTP 409). */
public class ConflitoException extends RuntimeException {

	public ConflitoException(String mensagem) {
		super(mensagem);
	}
}
