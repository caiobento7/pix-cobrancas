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

	private volatile Instant agora = Instant.parse("2026-11-06T12:00:00Z");

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
