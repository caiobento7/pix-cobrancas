package dev.caiobento.pix.cobranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import dev.caiobento.pix.TesteDeIntegracao;

class DevolucaoApiTest extends TesteDeIntegracao {

	private String cobrancaPaga(String valor) throws Exception {
		String txid = criarCobranca(valor);
		enviarWebhook(psp.simularPagamento(txid)).andExpect(status().isOk());
		return txid;
	}

	private ResultActions devolver(String txid, String id, String valor) throws Exception {
		return api.perform(put("/cobrancas/{txid}/devolucoes/{id}", txid, id).contentType(MediaType.APPLICATION_JSON)
			.content("{\"valor\": \"" + valor + "\", \"motivo\": \"Pedido cancelado\"}"));
	}

	@Test
	void devolucaoParcialEDepoisOResto() throws Exception {
		String txid = cobrancaPaga("100.00");

		devolver(txid, "parcial" + txid.substring(0, 8), "30.00").andExpect(status().isCreated())
			.andExpect(jsonPath("$.valor").value("30.00"))
			.andExpect(jsonPath("$.status").value("SOLICITADA"));
		api.perform(get("/cobrancas/{txid}", txid)).andExpect(jsonPath("$.status").value("PAGA"));

		devolver(txid, "resto" + txid.substring(0, 8), "70.00").andExpect(status().isCreated());
		api.perform(get("/cobrancas/{txid}", txid))
			.andExpect(jsonPath("$.status").value("DEVOLVIDA"))
			.andExpect(jsonPath("$.devolucoes.length()").value(2));
	}

	@Test
	void repetirOMesmoPedidoNaoDevolveDuasVezes() throws Exception {
		String txid = cobrancaPaga("50.00");
		String id = "repetida" + txid.substring(0, 8);

		devolver(txid, id, "20.00").andExpect(status().isCreated());
		devolver(txid, id, "20.00").andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));

		api.perform(get("/cobrancas/{txid}", txid)).andExpect(jsonPath("$.devolucoes.length()").value(1));
		assertThat(psp.devolucoesSolicitadas()).containsKey(id);
	}

	@Test
	void mesmoIdComOutroValorDa409() throws Exception {
		String txid = cobrancaPaga("50.00");
		String id = "conflito" + txid.substring(0, 8);
		devolver(txid, id, "20.00").andExpect(status().isCreated());

		devolver(txid, id, "25.00").andExpect(status().isConflict());
	}

	@Test
	void devolverMaisQueOPagoDa422() throws Exception {
		String txid = cobrancaPaga("50.00");

		devolver(txid, "demais" + txid.substring(0, 8), "50.01").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.detail").value("A devolução de 50.01 passa do valor disponível de 50.00."));
	}

	@Test
	void devolverCobrancaNaoPagaDa422() throws Exception {
		String txid = criarCobranca("50.00");

		devolver(txid, "naopaga" + txid.substring(0, 8), "10.00").andExpect(status().isUnprocessableContent());
	}

	@Test
	void idDeDevolucaoComCaractereInvalidoDa400() throws Exception {
		String txid = cobrancaPaga("50.00");

		devolver(txid, "com-hifen", "10.00").andExpect(status().isBadRequest());
	}
}
