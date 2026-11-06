package dev.caiobento.pix.cobranca;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CobrancaRepository extends JpaRepository<Cobranca, UUID> {

	Optional<Cobranca> findByTxid(String txid);

	/** Lote da conciliação: as mais antigas primeiro, no máximo 100 por rodada. */
	List<Cobranca> findTop100ByStatusAndCriadaEmBeforeOrderByCriadaEmAsc(StatusCobranca status, Instant antesDe);
}
