package dev.caiobento.pix.webhook;

public class TokenInvalidoException extends RuntimeException {

	public TokenInvalidoException() {
		super("Token do webhook ausente ou inválido.");
	}
}
