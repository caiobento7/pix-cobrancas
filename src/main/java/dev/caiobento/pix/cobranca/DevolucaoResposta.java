package dev.caiobento.pix.cobranca;

import java.time.Instant;

public record DevolucaoResposta(String id, String valor, String motivo, String status, Instant solicitadaEm) {

	static DevolucaoResposta de(Devolucao d) {
		return new DevolucaoResposta(d.getId(), CobrancaResposta.texto(d.getValor()), d.getMotivo(), d.getStatus(),
				d.getSolicitadaEm());
	}
}
