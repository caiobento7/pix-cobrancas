package dev.caiobento.pix.pagamento;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.caiobento.pix.cobranca.CobrancaRepository;

/** Grava um Pix recebido e baixa a cobrança correspondente, tudo na mesma transação. */
@Service
public class RegistroDePagamento {

	private final CobrancaRepository cobrancas;
	private final PagamentoRecebidoRepository pagamentos;
	private final Clock relogio;

	public RegistroDePagamento(CobrancaRepository cobrancas, PagamentoRecebidoRepository pagamentos, Clock relogio) {
		this.cobrancas = cobrancas;
		this.pagamentos = pagamentos;
		this.relogio = relogio;
	}

	@Transactional
	public ResultadoPagamento registrar(PixRecebido pix, OrigemPagamento origem) {
		if (pagamentos.existsById(pix.endToEndId())) {
			return ResultadoPagamento.DUPLICADO;
		}
		ResultadoPagamento resultado = pix.txid() == null
				? ResultadoPagamento.COBRANCA_DESCONHECIDA
				: cobrancas.findByTxid(pix.txid())
					.map(cobranca -> cobranca.aplicarPagamento(pix.endToEndId(), pix.valor(), pix.horario()))
					.orElse(ResultadoPagamento.COBRANCA_DESCONHECIDA);

		// saveAndFlush faz o INSERT agora: se outro processo gravou o mesmo endToEndId (chave primária)
		// ou mexeu na mesma cobrança (versão), o erro aparece aqui e a transação inteira é desfeita.
		pagamentos.saveAndFlush(new PagamentoRecebido(pix, origem, resultado, relogio.instant()));
		return resultado;
	}
}
