package dev.caiobento.pix.pagamento;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Um Pix liquidado, como o PSP informa no webhook ou na consulta.
 *
 * @param endToEndId identificador único da transação no SPI (32 caracteres)
 * @param txid       identificador da cobrança; pode faltar em Pix avulso
 */
public record PixRecebido(String endToEndId, String txid, BigDecimal valor, Instant horario) {
}
