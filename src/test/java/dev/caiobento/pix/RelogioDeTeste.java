package dev.caiobento.pix;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Relógio que o teste adianta na mão, para simular o tempo passando. */
public class RelogioDeTeste extends Clock {

	private static final Instant INICIO = Instant.parse("2026-11-06T12:00:00Z");

	private volatile Instant agora = INICIO;

	/** Os testes compartilham o mesmo contexto Spring; cada um começa com o relógio no mesmo ponto. */
	public void reiniciar() {
		agora = INICIO;
	}

	public void avancar(Duration tempo) {
		agora = agora.plus(tempo);
	}

	@Override
	public Instant instant() {
		return agora;
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		throw new UnsupportedOperationException();
	}

	@TestConfiguration
	public static class Config {

		@Bean
		@Primary
		RelogioDeTeste relogioDeTeste() {
			return new RelogioDeTeste();
		}
	}
}
