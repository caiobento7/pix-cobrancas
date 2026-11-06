package dev.caiobento.pix.cobranca;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/cobrancas")
public class CobrancaController {

	/** Mesmo formato de valor da API Pix: até 10 dígitos, ponto e exatamente 2 casas. */
	static final String FORMATO_VALOR = "\\d{1,10}\\.\\d{2}";

	private final CobrancaService servico;

	public CobrancaController(CobrancaService servico) {
		this.servico = servico;
	}

	@PostMapping
	public ResponseEntity<CobrancaResposta> criar(@Valid @RequestBody NovaCobranca pedido) {
		Duration expiracao = pedido.expiracaoSegundos() == null ? null
				: Duration.ofSeconds(pedido.expiracaoSegundos());
		CobrancaResposta cobranca = servico.criar(new BigDecimal(pedido.valor()), pedido.descricao(), expiracao);
		return ResponseEntity.created(URI.create("/cobrancas/" + cobranca.txid())).body(cobranca);
	}

	@GetMapping("/{txid}")
	public CobrancaResposta buscar(@PathVariable String txid) {
		return servico.buscar(txid);
	}

	/** PUT com o id escolhido pelo cliente: repetir a chamada é seguro. */
	@PutMapping("/{txid}/devolucoes/{id}")
	public ResponseEntity<DevolucaoResposta> devolver(@PathVariable String txid,
			@PathVariable @Pattern(regexp = "[a-zA-Z0-9]{1,35}") String id,
			@Valid @RequestBody NovaDevolucao pedido) {
		var resultado = servico.devolver(txid, id, new BigDecimal(pedido.valor()), pedido.motivo());
		HttpStatus status = resultado.criada() ? HttpStatus.CREATED : HttpStatus.OK;
		return ResponseEntity.status(status).body(resultado.devolucao());
	}

	public record NovaCobranca(
			@NotNull @Pattern(regexp = FORMATO_VALOR, message = "deve ter duas casas decimais, como 25.90") @DecimalMin(value = "0.01", message = "deve ser maior que zero") String valor,
			@Size(max = 140) String descricao,
			@Min(60) @Max(30 * 24 * 60 * 60) Integer expiracaoSegundos) {
	}

	public record NovaDevolucao(
			@NotNull @Pattern(regexp = FORMATO_VALOR, message = "deve ter duas casas decimais, como 25.90") @DecimalMin(value = "0.01", message = "deve ser maior que zero") String valor,
			@Size(max = 140) String motivo) {
	}
}
