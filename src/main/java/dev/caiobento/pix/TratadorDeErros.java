package dev.caiobento.pix;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import dev.caiobento.pix.cobranca.CobrancaNaoEncontradaException;
import dev.caiobento.pix.cobranca.ConflitoException;
import dev.caiobento.pix.webhook.TokenInvalidoException;

/** Erros no formato Problem Details (RFC 9457). Validação de entrada (400) vem da classe mãe. */
@RestControllerAdvice
public class TratadorDeErros extends ResponseEntityExceptionHandler {

	/** Diz qual campo está errado e por quê, em vez do genérico "Invalid request content". */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException erro,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> campos = new LinkedHashMap<>();
		erro.getBindingResult().getFieldErrors().forEach(e -> campos.putIfAbsent(e.getField(), e.getDefaultMessage()));
		ProblemDetail problema = erro.getBody();
		problema.setDetail("Há campos inválidos no pedido.");
		problema.setProperty("campos", campos);
		return handleExceptionInternal(erro, problema, headers, status, request);
	}

	@ExceptionHandler(CobrancaNaoEncontradaException.class)
	ProblemDetail naoEncontrada(CobrancaNaoEncontradaException erro) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, erro.getMessage());
	}

	@ExceptionHandler(RegraDeNegocioException.class)
	ProblemDetail regraDeNegocio(RegraDeNegocioException erro) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, erro.getMessage());
	}

	@ExceptionHandler(ConflitoException.class)
	ProblemDetail conflito(ConflitoException erro) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, erro.getMessage());
	}

	/** Dois pedidos iguais ao mesmo tempo: o banco recusou o segundo. */
	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail violacaoDeIntegridade(DataIntegrityViolationException erro) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"O pedido conflita com outro feito ao mesmo tempo. Tente de novo.");
	}

	@ExceptionHandler(TokenInvalidoException.class)
	ProblemDetail tokenInvalido(TokenInvalidoException erro) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, erro.getMessage());
	}
}
