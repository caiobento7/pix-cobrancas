package dev.caiobento.pix.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties("pix")
public record PixProperties(
		@Valid @NotNull Webhook webhook,
		@DefaultValue("PT1H") Duration expiracaoPadrao,
		@Valid @NotNull Conciliacao conciliacao) {

	public record Webhook(@NotBlank String token) {
	}

	/**
	 * @param idadeMinima só consulta cobranças criadas há mais tempo que isso, para dar tempo
	 *                    de o webhook chegar antes.
	 */
	public record Conciliacao(
			@DefaultValue("true") boolean agendada,
			@DefaultValue("PT1M") Duration intervalo,
			@DefaultValue("PT2M") Duration idadeMinima) {
	}
}
