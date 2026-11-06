package dev.caiobento.pix.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import dev.caiobento.pix.TesteDeIntegracao;
import dev.caiobento.pix.pagamento.PagamentoRecebidoRepository;
import dev.caiobento.pix.pagamento.PixRecebido;

class WebhookPixTest extends TesteDeIntegracao {

	@Autowired
	private PagamentoRecebidoRepository pagamentos;

	@Test
	void webhookBaixaACobranca() throws Exception {
		String txid = criarCobranca("25.90");
		PixRecebido pix = psp.simularPagamento(txid);

		enviarWebhook(pix).andExpect(status().isOk())
			.andExpect(jsonPath("$.processados[0].resultado").value("APLICADO"));

		api.perform(get("/cobrancas/{txid}", txid))
			.andExpect(jsonPath("$.status").value("PAGA"))
			.andExpect(jsonPath("$.pagamento.endToEndId").value(pix.endToEndId()))
			.andExpect(jsonPath("$.pagamento.valor").value("25.90"));
	}

	@Test
	void webhookRepetidoNaoPagaDuasVezes() throws Exception {
		String txid = criarCobranca("25.90");
		PixRecebido pix = psp.simularPagamento(txid);

		enviarWebhook(pix).andExpect(jsonPath("$.processados[0].resultado").value("APLICADO"));
		enviarWebhook(pix).andExpect(status().isOk())
			.andExpect(jsonPath("$.processados[0].resultado").value("DUPLICADO"));
		enviarWebhook(pix).andExpect(jsonPath("$.processados[0].resultado").value("DUPLICADO"));

		assertThat(pagamentos.countByTxid(txid)).isEqualTo(1);
	}

	/** O PSP reenviando o mesmo webhook em paralelo: só uma requisição pode baixar a cobrança. */
	@Test
	void webhooksSimultaneosDoMesmoPixGravamUmaVezSo() throws Exception {
		String txid = criarCobranca("80.00");
		PixRecebido pix = psp.simularPagamento(txid);
		int requisicoes = 8;
		CountDownLatch largada = new CountDownLatch(1);

		List<Future<String>> respostas = new ArrayList<>();
		try (ExecutorService threads = Executors.newFixedThreadPool(requisicoes)) {
			for (int i = 0; i < requisicoes; i++) {
				Callable<String> chamada = () -> {
					largada.await();
					var resposta = enviarWebhook(pix).andReturn().getResponse();
					assertThat(resposta.getStatus()).isEqualTo(200);
					return resposta.getContentAsString();
				};
				respostas.add(threads.submit(chamada));
			}
			largada.countDown();
		}

		long aplicados = 0;
		for (Future<String> resposta : respostas) {
			if (resposta.get().contains("\"APLICADO\"")) {
				aplicados++;
			}
		}
		assertThat(aplicados).isEqualTo(1);
		assertThat(pagamentos.countByTxid(txid)).isEqualTo(1);
	}

	@Test
	void pixDeCobrancaDesconhecidaFicaRegistradoSemErro() throws Exception {
		enviarWebhook("E00000000202611061200desconhec01", "txidquenaoexiste0000000000000001", "10.00")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.processados[0].resultado").value("COBRANCA_DESCONHECIDA"));
	}

	@Test
	void pixSemTxidEhAceito() throws Exception {
		enviarWebhook("E00000000202611061200semtxid0001", null, "10.00").andExpect(status().isOk())
			.andExpect(jsonPath("$.processados[0].resultado").value("COBRANCA_DESCONHECIDA"));
	}

	@Test
	void valorDivergenteNaoBaixaACobranca() throws Exception {
		String txid = criarCobranca("100.00");

		enviarWebhook("E00000000202611061200divergent01", txid, "10.00")
			.andExpect(jsonPath("$.processados[0].resultado").value("VALOR_DIVERGENTE"));

		api.perform(get("/cobrancas/{txid}", txid)).andExpect(jsonPath("$.status").value("ATIVA"));
	}

	@Test
	void loteComVariosPix() throws Exception {
		String txid1 = criarCobranca("1.00");
		String txid2 = criarCobranca("2.00");
		PixRecebido pix1 = psp.simularPagamento(txid1);
		PixRecebido pix2 = psp.simularPagamento(txid2);

		api.perform(post("/webhooks/pix").header("X-Webhook-Token", TOKEN)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"pix": [
					  {"endToEndId": "%s", "txid": "%s", "valor": "1.00", "horario": "2026-11-06T12:00:01Z"},
					  {"endToEndId": "%s", "txid": "%s", "valor": "2.00", "horario": "2026-11-06T12:00:02Z"}
					]}""".formatted(pix1.endToEndId(), txid1, pix2.endToEndId(), txid2)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.processados.length()").value(2))
			.andExpect(jsonPath("$.processados[*].resultado").value(org.hamcrest.Matchers.everyItem(
					org.hamcrest.Matchers.is("APLICADO"))));
	}

	@Test
	void semTokenOuComTokenErradoDa401() throws Exception {
		String corpo = "{\"pix\": [{\"endToEndId\": \"E00000000202611061200tokenerr001\", \"valor\": \"1.00\", "
				+ "\"horario\": \"2026-11-06T12:00:00Z\"}]}";

		api.perform(post("/webhooks/pix").contentType(MediaType.APPLICATION_JSON).content(corpo))
			.andExpect(status().isUnauthorized());
		api.perform(post("/webhooks/pix").header("X-Webhook-Token", "chute")
			.contentType(MediaType.APPLICATION_JSON)
			.content(corpo)).andExpect(status().isUnauthorized());

		assertThat(pagamentos.existsById("E00000000202611061200tokenerr001")).isFalse();
	}

	/** Sem token, nem o formato do corpo é avaliado: a resposta é 401, não 400. */
	@Test
	void tokenEhConferidoAntesDeValidarOCorpo() throws Exception {
		api.perform(post("/webhooks/pix").contentType(MediaType.APPLICATION_JSON).content("{\"pix\": []}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void endToEndIdForaDoFormatoDa400() throws Exception {
		enviarWebhook("curto", null, "1.00").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.campos['pix[0].endToEndId']").value("deve ter 32 caracteres alfanuméricos"));
	}
}
