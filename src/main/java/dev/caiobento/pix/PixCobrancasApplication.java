package dev.caiobento.pix;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PixCobrancasApplication {

	public static void main(String[] args) {
		SpringApplication.run(PixCobrancasApplication.class, args);
	}

}
