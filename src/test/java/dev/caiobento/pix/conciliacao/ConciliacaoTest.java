package dev.caiobento.pix.conciliacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.caiobento.pix.TesteDeIntegracao;
import dev.caiobento.pix.pagamento.OrigemPagamento;
import dev.caiobento.pix.pagamento.PagamentoRecebidoRepository;
import dev.caiobento.pix.pagamento.PixRecebido;

class ConciliacaoTest extends TesteDeIntegracao {

	@Autowired
	private Conciliacao conciliacao;

	@Autowired
	private PagamentoRecebidoRepository pagamentos;

	@Test
	void achaOPagamentoQuandoOWebhookSePerde() throws Exception {
		String txid = criarCobranca("37.50");
		PixRecebido pix = psp.simularPagamento(txid);
		// O webhook nunca chega.
		relogio.avancar(Duration.ofSeconds(1));

		conciliacao.executar();

		api.perform(get("/cobrancas/{txid}", txid))
			.andExpect(jsonPath("$.status").value("PAGA"))
			.andExpect(jsonPath("$.pagamento.endToEndId").value(pix.endToEndId()));
		assertThat(pagamentos.findById(pix.endToEndId()).orElseThrow().getOrigem())
			.isEqualTo(OrigemPagamento.CONCILIACAO);
	}

	@Test
	void webhookAtrasadoDepoisDaConciliacaoNaoDuplica() throws Exception {
		String txid = criarCobranca("37.50");
		PixRecebido pix = psp.simularPagamento(txid);
		relogio.avancar(Duration.ofSeconds(1));
		conciliacao.executar();

		enviarWebhook(pix).andExpect(jsonPath("$.processados[0].resultado").value("DUPLICADO"));

		assertThat(pagamentos.countByTxid(txid)).isEqualTo(1);
	}

	@Test
	void expiraCobrancaVencidaSemPagamento() throws Exception {
		String txid = criarCobranca("12.00");
		relogio.avancar(Duration.ofSeconds(1));

		conciliacao.executar();
		api.perform(get("/cobrancas/{txid}", txid)).andExpect(jsonPath("$.status").value("ATIVA"));

		relogio.avancar(Duration.ofHours(2));
		conciliacao.executar();
		api.perform(get("/cobrancas/{txid}", txid)).andExpect(jsonPath("$.status").value("EXPIRADA"));
	}
}
