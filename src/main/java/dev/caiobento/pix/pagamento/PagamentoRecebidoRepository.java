package dev.caiobento.pix.pagamento;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PagamentoRecebidoRepository extends JpaRepository<PagamentoRecebido, String> {

	long countByTxid(String txid);
}
