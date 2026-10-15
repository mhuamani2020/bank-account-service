package com.nttdata.bank_account_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuración del {@link WebClient} compartido por los adaptadores de clientes
 * inter-microservicio.
 * <p>
 * Expone un {@code WebClient.Builder} como bean para que cada adaptador construya su
 * propio cliente conservando la misma configuración base.
 *
 * @author NTT Data
 */
@Configuration
public class WebClientConfig {

    /** Crea el builder que usan los adaptadores de clientes para construir sus WebClient. */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
