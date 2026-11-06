package dev.caiobento.pix.cobranca;

import static org.hamcrest.Matchers.hasLength;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

import dev.caiobento.pix.TesteDeIntegracao;

class CobrancaApiTest extends TesteDeIntegracao {

	@Test
	void criaCobrancaAtivaComTxidECopiaECola() throws Exception {
		api.perform(post("/cobrancas").contentType(MediaType.APPLICATION_JSON)
			.content("{\"valor\": \"49.90\", \"descricao\": \"Camisa\", \"expiracaoSegundos\": 600}"))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", startsWith("/cobrancas/")))
			.andExpect(jsonPath("$.txid", hasLength(32)))
			.andExpect(jsonPath("$.valor").value("49.90"))
			.andExpect(jsonPath("$.status").value("ATIVA"))
			.andExpect(jsonPath("$.pixCopiaECola", startsWith("000201")))
			.andExpect(jsonPath("$.criadaEm").value("2026-11-06T12:00:00Z"))
			.andExpect(jsonPath("$.expiraEm").value("2026-11-06T12:10:00Z"));
	}

	@Test
	void buscaCobrancaPeloTxid() throws Exception {
		String txid = criarCobranca("15.00");

		api.perform(get("/cobrancas/{txid}", txid))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.txid").value(txid))
			.andExpect(jsonPath("$.pagamento").isEmpty())
			.andExpect(jsonPath("$.devolucoes").isEmpty());
	}

	@Test
	void cobrancaInexistenteDa404() throws Exception {
		api.perform(get("/cobrancas/{txid}", "naoexiste000000000000000000000000"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value("Cobrança naoexiste000000000000000000000000 não encontrada."));
	}

	/** Valor precisa vir como texto com duas casas, igual à API Pix, e ser maior que zero. */
	@ParameterizedTest
	@ValueSource(strings = { "10", "10.5", "1.234", "0.00", "-5.00", "abc" })
	void recusaValorInvalido(String valor) throws Exception {
		api.perform(post("/cobrancas").contentType(MediaType.APPLICATION_JSON)
			.content("{\"valor\": \"" + valor + "\"}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void erroDeValidacaoDizQualCampoEstaErrado() throws Exception {
		api.perform(post("/cobrancas").contentType(MediaType.APPLICATION_JSON).content("{\"valor\": \"10\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Há campos inválidos no pedido."))
			.andExpect(jsonPath("$.campos.valor").value("deve ter duas casas decimais, como 25.90"));
	}

	@Test
	void recusaExpiracaoMenorQueUmMinuto() throws Exception {
		api.perform(post("/cobrancas").contentType(MediaType.APPLICATION_JSON)
			.content("{\"valor\": \"10.00\", \"expiracaoSegundos\": 30}"))
			.andExpect(status().isBadRequest());
	}
}
