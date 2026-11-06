package dev.caiobento.pix.cobranca;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class Devolucao {

	/** Escolhido por quem pede a devolução; repetir o id não devolve duas vezes. */
	@Id
	@Column(length = 35)
	private String id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cobranca_id")
	private Cobranca cobranca;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal valor;

	@Column(length = 140)
	private String motivo;

	/** O PSP processa a devolução de forma assíncrona; aqui guardamos que ela foi pedida. */
	@Column(nullable = false, length = 20)
	private String status;

	@Column(nullable = false)
	private Instant solicitadaEm;

	protected Devolucao() {
	}

	Devolucao(String id, Cobranca cobranca, BigDecimal valor, String motivo, Instant solicitadaEm) {
		this.id = id;
		this.cobranca = cobranca;
		this.valor = valor;
		this.motivo = motivo;
		this.solicitadaEm = solicitadaEm;
		this.status = "SOLICITADA";
	}

	public String getId() {
		return id;
	}

	public Cobranca getCobranca() {
		return cobranca;
	}

	public BigDecimal getValor() {
		return valor;
	}

	public String getMotivo() {
		return motivo;
	}

	public String getStatus() {
		return status;
	}

	public Instant getSolicitadaEm() {
		return solicitadaEm;
	}
}
