package dev.caiobento.pix.cobranca;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.caiobento.pix.RegraDeNegocioException;
import dev.caiobento.pix.pagamento.ResultadoPagamento;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Version;

@Entity
public class Cobranca {

	@Id
	private UUID id;

	@Column(nullable = false, unique = true, length = 35)
	private String txid;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal valor;

	@Column(length = 140)
	private String descricao;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StatusCobranca status;

	@Column(name = "pix_copia_e_cola", nullable = false, length = 512)
	private String pixCopiaECola;

	@Column(nullable = false)
	private Instant criadaEm;

	@Column(nullable = false)
	private Instant expiraEm;

	private Instant pagaEm;

	@Column(unique = true, length = 32)
	private String endToEndId;

	@Column(precision = 12, scale = 2)
	private BigDecimal valorPago;

	/**
	 * Trava otimista: se o webhook e a conciliação mexerem na mesma cobrança ao mesmo tempo,
	 * um deles falha em vez de sobrescrever o outro.
	 */
	@Version
	@Column(nullable = false)
	private long versao;

	@OneToMany(mappedBy = "cobranca", cascade = CascadeType.ALL)
	private List<Devolucao> devolucoes = new ArrayList<>();

	protected Cobranca() {
	}

	public Cobranca(String txid, BigDecimal valor, String descricao, String pixCopiaECola, Instant criadaEm,
			Instant expiraEm) {
		this.id = UUID.randomUUID();
		this.txid = txid;
		this.valor = valor;
		this.descricao = descricao;
		this.pixCopiaECola = pixCopiaECola;
		this.criadaEm = criadaEm;
		this.expiraEm = expiraEm;
		this.status = StatusCobranca.ATIVA;
	}

	/**
	 * Aplica um Pix recebido. Não lança exceção para casos esperados (repetido, valor errado):
	 * devolve o resultado para ele ser gravado e o PSP não ficar reenviando o webhook.
	 */
	public ResultadoPagamento aplicarPagamento(String endToEndId, BigDecimal valorRecebido, Instant horario) {
		if (endToEndId.equals(this.endToEndId)) {
			return ResultadoPagamento.DUPLICADO;
		}
		if (status == StatusCobranca.PAGA || status == StatusCobranca.DEVOLVIDA) {
			return ResultadoPagamento.COBRANCA_JA_PAGA;
		}
		if (valorRecebido.compareTo(valor) != 0) {
			return ResultadoPagamento.VALOR_DIVERGENTE;
		}
		// Uma cobrança EXPIRADA aqui ainda aceita o pagamento: se o Pix foi liquidado, o dinheiro entrou.
		this.status = StatusCobranca.PAGA;
		this.endToEndId = endToEndId;
		this.valorPago = valorRecebido;
		this.pagaEm = horario;
		return ResultadoPagamento.APLICADO;
	}

	/** @return true se a cobrança estava ativa e venceu. */
	public boolean expirarSeVencida(Instant agora) {
		if (status == StatusCobranca.ATIVA && !agora.isBefore(expiraEm)) {
			status = StatusCobranca.EXPIRADA;
			return true;
		}
		return false;
	}

	public Devolucao registrarDevolucao(String idDevolucao, BigDecimal valorDevolucao, String motivo, Instant agora) {
		if (status != StatusCobranca.PAGA) {
			throw new RegraDeNegocioException("Só dá para devolver uma cobrança paga. Status atual: " + status + ".");
		}
		BigDecimal disponivel = valorPago.subtract(totalDevolvido());
		if (valorDevolucao.compareTo(disponivel) > 0) {
			throw new RegraDeNegocioException(
					"A devolução de " + valorDevolucao + " passa do valor disponível de " + disponivel + ".");
		}
		Devolucao devolucao = new Devolucao(idDevolucao, this, valorDevolucao, motivo, agora);
		devolucoes.add(devolucao);
		if (totalDevolvido().compareTo(valorPago) == 0) {
			status = StatusCobranca.DEVOLVIDA;
		}
		return devolucao;
	}

	public BigDecimal totalDevolvido() {
		return devolucoes.stream().map(Devolucao::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	public UUID getId() {
		return id;
	}

	public String getTxid() {
		return txid;
	}

	public BigDecimal getValor() {
		return valor;
	}

	public String getDescricao() {
		return descricao;
	}

	public StatusCobranca getStatus() {
		return status;
	}

	public String getPixCopiaECola() {
		return pixCopiaECola;
	}

	public Instant getCriadaEm() {
		return criadaEm;
	}

	public Instant getExpiraEm() {
		return expiraEm;
	}

	public Instant getPagaEm() {
		return pagaEm;
	}

	public String getEndToEndId() {
		return endToEndId;
	}

	public BigDecimal getValorPago() {
		return valorPago;
	}

	public List<Devolucao> getDevolucoes() {
		return List.copyOf(devolucoes);
	}
}
