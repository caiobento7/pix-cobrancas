package dev.caiobento.pix.pagamento;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;

/** Registro de cada Pix processado. A chave primária (endToEndId) impede processar o mesmo Pix duas vezes. */
@Entity
public class PagamentoRecebido implements Persistable<String> {

	@Id
	@Column(length = 32)
	private String endToEndId;

	@Column(length = 35)
	private String txid;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal valor;

	@Column(nullable = false)
	private Instant horario;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OrigemPagamento origem;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ResultadoPagamento resultado;

	@Column(nullable = false)
	private Instant recebidoEm;

	/** Sempre novo: força INSERT, para o banco recusar um endToEndId repetido em vez de fazer UPDATE. */
	@Transient
	private boolean novo = true;

	protected PagamentoRecebido() {
		this.novo = false;
	}

	public PagamentoRecebido(PixRecebido pix, OrigemPagamento origem, ResultadoPagamento resultado,
			Instant recebidoEm) {
		this.endToEndId = pix.endToEndId();
		this.txid = pix.txid();
		this.valor = pix.valor();
		this.horario = pix.horario();
		this.origem = origem;
		this.resultado = resultado;
		this.recebidoEm = recebidoEm;
	}

	@Override
	public String getId() {
		return endToEndId;
	}

	@Override
	public boolean isNew() {
		return novo;
	}

	public String getTxid() {
		return txid;
	}

	public BigDecimal getValor() {
		return valor;
	}

	public OrigemPagamento getOrigem() {
		return origem;
	}

	public ResultadoPagamento getResultado() {
		return resultado;
	}
}
