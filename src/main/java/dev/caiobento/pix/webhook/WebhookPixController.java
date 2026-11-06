package dev.caiobento.pix.webhook;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.caiobento.pix.pagamento.OrigemPagamento;
import dev.caiobento.pix.pagamento.PixRecebido;
import dev.caiobento.pix.pagamento.ProcessadorDePagamentos;
import dev.caiobento.pix.pagamento.ResultadoPagamento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Recebe as notificações do PSP. O Banco Central acrescenta "/pix" à URL cadastrada, por isso a
 * URL a cadastrar no PSP é ".../webhooks".
 *
 * <p>O token é conferido antes, no {@link TokenDoWebhookInterceptor}.
 *
 * <p>Responde 200 mesmo para Pix repetidos ou desconhecidos: se respondesse erro, o PSP reenviaria
 * o mesmo webhook várias vezes. Só falhas de verdade (banco fora do ar) viram 5xx, e aí o reenvio
 * é bem-vindo.
 */
@RestController
@RequestMapping("/webhooks")
public class WebhookPixController {

	private final ProcessadorDePagamentos processador;

	public WebhookPixController(ProcessadorDePagamentos processador) {
		this.processador = processador;
	}

	@PostMapping("/pix")
	public RespostaWebhook receber(@Valid @RequestBody NotificacaoPix notificacao) {
		List<ItemProcessado> itens = notificacao.pix()
			.stream()
			.map(pix -> new ItemProcessado(pix.endToEndId(),
					processador.processar(pix.paraPixRecebido(), OrigemPagamento.WEBHOOK)))
			.toList();
		return new RespostaWebhook(itens);
	}

	/** Formato do webhook da API Pix: {"pix": [{"endToEndId", "txid", "valor", "horario"}]}. */
	public record NotificacaoPix(@NotEmpty List<@Valid @NotNull PixNotificado> pix) {
	}

	public record PixNotificado(
			@NotNull @Pattern(regexp = "[a-zA-Z0-9]{32}", message = "deve ter 32 caracteres alfanuméricos") String endToEndId,
			@Size(min = 26, max = 35) String txid,
			@NotNull @Pattern(regexp = "\\d{1,10}\\.\\d{2}", message = "deve ter duas casas decimais, como 25.90") String valor,
			@NotNull Instant horario) {

		PixRecebido paraPixRecebido() {
			return new PixRecebido(endToEndId, txid, new BigDecimal(valor), horario);
		}
	}

	public record ItemProcessado(String endToEndId, ResultadoPagamento resultado) {
	}

	public record RespostaWebhook(List<ItemProcessado> processados) {
	}
}
