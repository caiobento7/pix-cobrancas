package dev.caiobento.pix.psp;

import java.math.BigDecimal;
import java.time.Duration;

import dev.caiobento.pix.pagamento.PixRecebido;

/**
 * O PSP é o banco ou instituição de pagamento que conversa com o Pix do Banco Central.
 * Esta interface isola a aplicação dele: trocar de banco é escrever outra implementação.
 */
public interface PspClient {

	/** Cria a cobrança imediata (PUT /cob/{txid} na API Pix) e devolve o "Pix copia e cola". */
	String criarCobranca(String txid, BigDecimal valor, Duration expiracao, String descricao);

	/** Consulta a cobrança (GET /cob/{txid}). Usado pela conciliação quando o webhook não chega. */
	ConsultaCobranca consultarCobranca(String txid);

	/** Pede a devolução (PUT /pix/{e2eid}/devolucao/{id}). O id torna o pedido idempotente no PSP também. */
	void solicitarDevolucao(String endToEndId, String idDevolucao, BigDecimal valor);

	/** @param pagamento o Pix que pagou a cobrança, ou null se ainda não foi paga. */
	record ConsultaCobranca(String txid, PixRecebido pagamento) {

		public boolean paga() {
			return pagamento != null;
		}
	}
}
