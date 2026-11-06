package dev.caiobento.pix.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import dev.caiobento.pix.webhook.TokenDoWebhookInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	private final TokenDoWebhookInterceptor tokenDoWebhook;

	public WebConfig(TokenDoWebhookInterceptor tokenDoWebhook) {
		this.tokenDoWebhook = tokenDoWebhook;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(tokenDoWebhook).addPathPatterns("/webhooks/**");
	}
}
