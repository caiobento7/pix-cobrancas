package dev.caiobento.pix;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

import dev.caiobento.pix.pagamento.PixRecebido;
import dev.caiobento.pix.psp.PspSimulado;

/** Sobe a aplicação inteira (banco H2 com as migrações do Flyway) e chama a API como um cliente faria. */
@SpringBootTest
@ActiveProfiles("test")
@Import(RelogioDeTeste.Config.class)
public abstract class TesteDeIntegracao {

	protected static final String TOKEN = "token-de-teste";

	@Autowired
	private WebApplicationContext contexto;

	@Autowired
	protected PspSimulado psp;

	@Autowired
	protected RelogioDeTeste relogio;

	protected MockMvc api;

	@BeforeEach
	void montarApi() {
		api = MockMvcBuilders.webAppContextSetup(contexto).build();
	}

	/** Cria uma cobrança pela API e devolve o txid. */
	protected String criarCobranca(String valor) throws Exception {
		String corpo = api
			.perform(post("/cobrancas").contentType(MediaType.APPLICATION_JSON)
				.content("{\"valor\": \"" + valor + "\", \"descricao\": \"Pedido de teste\"}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(corpo, "$.txid");
	}

	protected ResultActions enviarWebhook(PixRecebido pix) throws Exception {
		return enviarWebhook(pix.endToEndId(), pix.txid(), pix.valor().toPlainString());
	}

	protected ResultActions enviarWebhook(String endToEndId, String txid, String valor) throws Exception {
		String txidJson = txid == null ? "" : "\"txid\": \"" + txid + "\", ";
		return api.perform(post("/webhooks/pix").header("X-Webhook-Token", TOKEN)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"pix\": [{\"endToEndId\": \"" + endToEndId + "\", " + txidJson + "\"valor\": \"" + valor
					+ "\", \"horario\": \"2026-11-06T12:00:05Z\"}]}"));
	}
}
