package dev.caiobento.pix.pagamento;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * Porta de entrada única para Pix recebidos, venham do webhook ou da conciliação.
 *
 * <p>Se o mesmo Pix chegar duas vezes ao mesmo tempo (o PSP reenviando o webhook enquanto a
 * conciliação consulta), só uma transação consegue gravar. A outra falha na chave primária ou na
 * versão da cobrança; aqui ela é reconhecida como duplicada em vez de virar erro 500.
 */
@Service
public class ProcessadorDePagamentos {

	private static final Logger log = LoggerFactory.getLogger(ProcessadorDePagamentos.class);
	private static final int MAX_TENTATIVAS = 3;

	private final RegistroDePagamento registro;
	private final PagamentoRecebidoRepository pagamentos;

	public ProcessadorDePagamentos(RegistroDePagamento registro, PagamentoRecebidoRepository pagamentos) {
		this.registro = registro;
		this.pagamentos = pagamentos;
	}

	public ResultadoPagamento processar(PixRecebido pix, OrigemPagamento origem) {
		for (int tentativa = 1;; tentativa++) {
			try {
				ResultadoPagamento resultado = registro.registrar(pix, origem);
				registrarNoLog(pix, origem, resultado);
				return resultado;
			}
			catch (DataIntegrityViolationException | OptimisticLockingFailureException conflito) {
				if (pagamentos.existsById(pix.endToEndId())) {
					registrarNoLog(pix, origem, ResultadoPagamento.DUPLICADO);
					return ResultadoPagamento.DUPLICADO;
				}
				// Outro Pix mexeu na mesma cobrança ao mesmo tempo: tenta de novo com os dados atualizados.
				if (tentativa >= MAX_TENTATIVAS) {
					throw conflito;
				}
			}
		}
	}

	private void registrarNoLog(PixRecebido pix, OrigemPagamento origem, ResultadoPagamento resultado) {
		switch (resultado) {
			case APLICADO, DUPLICADO -> log.info("Pix {} (txid {}) via {}: {}", pix.endToEndId(), pix.txid(), origem,
					resultado);
			default -> log.warn("Pix {} (txid {}, valor {}) via {} precisa de análise: {}", pix.endToEndId(),
					pix.txid(), pix.valor(), origem, resultado);
		}
	}
}
