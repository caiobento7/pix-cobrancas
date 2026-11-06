package dev.caiobento.pix;

/** Pedido válido no formato, mas que as regras não permitem (vira HTTP 422). */
public class RegraDeNegocioException extends RuntimeException {

	public RegraDeNegocioException(String mensagem) {
		super(mensagem);
	}
}
