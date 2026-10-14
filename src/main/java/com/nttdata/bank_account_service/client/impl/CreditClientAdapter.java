package com.nttdata.bank_account_service.client.impl;

import com.nttdata.bank_account_service.client.CreditClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class CreditClientAdapter implements CreditClient { // <-- Implementamos la interfaz

    private final WebClient webClient;
    private final ReactiveCircuitBreaker reactiveCircuitBreaker;

    @Value("${app.urls.credit-service:http://localhost:8083/credits/}")
    private String creditServiceUrl;

    public CreditClientAdapter(WebClient.Builder webClientBuilder, ReactiveCircuitBreakerFactory circuitBreakerFactory) {
        this.webClient = webClientBuilder.build();
        this.reactiveCircuitBreaker = circuitBreakerFactory.create("creditServiceCB");
    }

    @Override
    public Mono<Boolean> customerHasCreditCard(String customerId) {
        log.info("Consultando al credit-service si el cliente {} tiene tarjeta de crédito", customerId);

        Mono<Boolean> apiCall = webClient.get()
                .uri(creditServiceUrl + "customer/" + customerId + "/has-credit-card")
                .retrieve()
                .bodyToMono(Boolean.class);

        return reactiveCircuitBreaker.run(apiCall, throwable -> {
            log.error("Error al consultar credit-service o timeout excedido (Circuit Breaker abierto): {}", throwable.getMessage());
            return Mono.just(false);
        });
    }
}
