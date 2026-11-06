package dev.caiobento.pix.cobranca;

public class CobrancaNaoEncontradaException extends RuntimeException {

	public CobrancaNaoEncontradaException(String txid) {
		super("Cobrança " + txid + " não encontrada.");
	}
}
