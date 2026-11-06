package dev.caiobento.pix.webhook;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import dev.caiobento.pix.config.PixProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Confere o token antes de qualquer outra coisa, inclusive antes de ler e validar o corpo:
 * quem não tem o token recebe 401 e não descobre nada sobre o formato esperado.
 *
 * <p>Em produção o PSP também se autentica com mTLS; o token é uma segunda camada.
 */
@Component
public class TokenDoWebhookInterceptor implements HandlerInterceptor {

	static final String CABECALHO = "X-Webhook-Token";

	private final byte[] tokenEsperado;

	public TokenDoWebhookInterceptor(PixProperties propriedades) {
		this.tokenEsperado = propriedades.webhook().token().getBytes(StandardCharsets.UTF_8);
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		String token = request.getHeader(CABECALHO);
		// Comparação em tempo constante, para não vazar o token pelo tempo de resposta.
		if (token == null || !MessageDigest.isEqual(tokenEsperado, token.getBytes(StandardCharsets.UTF_8))) {
			throw new TokenInvalidoException();
		}
		return true;
	}
}
