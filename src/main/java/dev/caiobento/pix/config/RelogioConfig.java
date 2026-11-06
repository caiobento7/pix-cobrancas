package dev.caiobento.pix.config;

import java.time.Clock;
import java.time.ZoneOffset;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
public class RelogioConfig {

	/**
	 * Relógio injetável: nos testes dá para controlar o "agora". Em milissegundos, para o horário
	 * devolvido na criação ser igual ao que o banco guarda.
	 */
	@Bean
	Clock relogio() {
		return Clock.tickMillis(ZoneOffset.UTC);
	}

	@Configuration
	@EnableScheduling
	@ConditionalOnProperty(name = "pix.conciliacao.agendada", havingValue = "true", matchIfMissing = true)
	static class Agendamento {
	}
}
