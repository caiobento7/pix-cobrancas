package dev.caiobento.pix.cobranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.caiobento.pix.RegraDeNegocioException;
import dev.caiobento.pix.pagamento.ResultadoPagamento;

/** Regras da cobrança, sem Spring nem banco. */
class CobrancaTest {

	private static final Instant AGORA = Instant.parse("2026-11-06T12:00:00Z");
	private static final String E2E = "E00000000202611061200aaaaaaaaaaa";
	private static final String OUTRO_E2E = "E00000000202611061201bbbbbbbbbbb";

	private Cobranca cobrancaDe(String valor) {
		return new Cobranca("txid0000000000000000000000000001", new BigDecimal(valor), "Pedido 42", "copia-e-cola",
				AGORA, AGORA.plus(Duration.ofHours(1)));
	}

	@Nested
	class Pagamento {

		@Test
		void pagamentoComValorCertoBaixaACobranca() {
			Cobranca cobranca = cobrancaDe("25.90");

			var resultado = cobranca.aplicarPagamento(E2E, new BigDecimal("25.90"), AGORA);

			assertThat(resultado).isEqualTo(ResultadoPagamento.APLICADO);
			assertThat(cobranca.getStatus()).isEqualTo(StatusCobranca.PAGA);
			assertThat(cobranca.getEndToEndId()).isEqualTo(E2E);
			assertThat(cobranca.getPagaEm()).isEqualTo(AGORA);
		}

		@Test
		void valorComOutraEscalaContaComoIgual() {
			Cobranca cobranca = cobrancaDe("10.00");

			assertThat(cobranca.aplicarPagamento(E2E, new BigDecimal("10.0"), AGORA))
				.isEqualTo(ResultadoPagamento.APLICADO);
		}

		@Test
		void mesmoPixDuasVezesNaoMudaNada() {
			Cobranca cobranca = cobrancaDe("25.90");
			cobranca.aplicarPagamento(E2E, new BigDecimal("25.90"), AGORA);

			var resultado = cobranca.aplicarPagamento(E2E, new BigDecimal("25.90"), AGORA.plusSeconds(5));

			assertThat(resultado).isEqualTo(ResultadoPagamento.DUPLICADO);
			assertThat(cobranca.getPagaEm()).isEqualTo(AGORA);
		}

		@Test
		void segundoPixDiferenteEmCobrancaJaPagaFicaParaDevolver() {
			Cobranca cobranca = cobrancaDe("25.90");
			cobranca.aplicarPagamento(E2E, new BigDecimal("25.90"), AGORA);

			var resultado = cobranca.aplicarPagamento(OUTRO_E2E, new BigDecimal("25.90"), AGORA);

			assertThat(resultado).isEqualTo(ResultadoPagamento.COBRANCA_JA_PAGA);
			assertThat(cobranca.getEndToEndId()).isEqualTo(E2E);
		}

		@Test
		void valorDiferenteNaoBaixaACobranca() {
			Cobranca cobranca = cobrancaDe("25.90");

			var resultado = cobranca.aplicarPagamento(E2E, new BigDecimal("2.59"), AGORA);

			assertThat(resultado).isEqualTo(ResultadoPagamento.VALOR_DIVERGENTE);
			assertThat(cobranca.getStatus()).isEqualTo(StatusCobranca.ATIVA);
		}

		@Test
		void pixLiquidadoDepoisDeExpirarAindaContaComoPago() {
			Cobranca cobranca = cobrancaDe("25.90");
			cobranca.expirarSeVencida(AGORA.plus(Duration.ofHours(2)));

			var resultado = cobranca.aplicarPagamento(E2E, new BigDecimal("25.90"), AGORA);

			assertThat(resultado).isEqualTo(ResultadoPagamento.APLICADO);
			assertThat(cobranca.getStatus()).isEqualTo(StatusCobranca.PAGA);
		}
	}

	@Nested
	class Expiracao {

		@Test
		void soExpiraDepoisDoPrazo() {
			Cobranca cobranca = cobrancaDe("25.90");

			assertThat(cobranca.expirarSeVencida(AGORA.plus(Duration.ofMinutes(59)))).isFalse();
			assertThat(cobranca.expirarSeVencida(AGORA.plus(Duration.ofHours(1)))).isTrue();
			assertThat(cobranca.getStatus()).isEqualTo(StatusCobranca.EXPIRADA);
		}

		@Test
		void cobrancaPagaNaoExpira() {
			Cobranca cobranca = cobrancaDe("25.90");
			cobranca.aplicarPagamento(E2E, new BigDecimal("25.90"), AGORA);

			assertThat(cobranca.expirarSeVencida(AGORA.plus(Duration.ofDays(1)))).isFalse();
			assertThat(cobranca.getStatus()).isEqualTo(StatusCobranca.PAGA);
		}
	}

	@Nested
	class Devolucoes {

		@Test
		void devolucoesParciaisAteOTotal() {
			Cobranca cobranca = cobrancaDe("100.00");
			cobranca.aplicarPagamento(E2E, new BigDecimal("100.00"), AGORA);

			cobranca.registrarDevolucao("dev1", new BigDecimal("30.00"), "Item faltando", AGORA);
			assertThat(cobranca.getStatus()).isEqualTo(StatusCobranca.PAGA);
			assertThat(cobranca.totalDevolvido()).isEqualByComparingTo("30.00");

			cobranca.registrarDevolucao("dev2", new BigDecimal("70.00"), "Pedido cancelado", AGORA);
			assertThat(cobranca.getStatus()).isEqualTo(StatusCobranca.DEVOLVIDA);
		}

		@Test
		void naoDevolveMaisQueOPago() {
			Cobranca cobranca = cobrancaDe("100.00");
			cobranca.aplicarPagamento(E2E, new BigDecimal("100.00"), AGORA);
			cobranca.registrarDevolucao("dev1", new BigDecimal("60.00"), null, AGORA);

			assertThatThrownBy(() -> cobranca.registrarDevolucao("dev2", new BigDecimal("40.01"), null, AGORA))
				.isInstanceOf(RegraDeNegocioException.class)
				.hasMessageContaining("40.00");
		}

		@Test
		void naoDevolveCobrancaQueNaoFoiPaga() {
			Cobranca cobranca = cobrancaDe("100.00");

			assertThatThrownBy(() -> cobranca.registrarDevolucao("dev1", new BigDecimal("1.00"), null, AGORA))
				.isInstanceOf(RegraDeNegocioException.class)
				.hasMessageContaining("ATIVA");
		}
	}
}
