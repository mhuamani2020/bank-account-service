package com.nttdata.bank_account_service.client.impl;

import com.nttdata.bank_account_service.client.CustomerClient;
import com.nttdata.bank_account_service.dto.CustomerDTO;
import com.nttdata.commons.resilience.client.ServiceFallbacks;
import com.nttdata.commons.resilience.client.ServiceNames;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * Adaptador reactivo de {@code customer-service} con el estándar de resiliencia del
 * proyecto ({@code bank-resilience-commons}).
 * <p>
 * La URL base no está hardcodeada: se lee de la propiedad externalizada
 * {@code app.urls.customer-service}, definida en los ficheros de configuración
 * ({@code application.yml} local y Config Server).
 *
 * @author NTT Data
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerClientAdapter implements CustomerClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${app.urls.customer-service}")
    private String customerServiceUrl;

    /**
     * Obtiene un cliente por ID.
     * <p>
     * Estandar de resiliencia: llamada protegida con {@link CircuitBreaker} + {@link TimeLimiter}.
     * Un 404 (cliente inexistente) es un resultado de negocio, no un fallo de disponibilidad:
     * se traduce a {@code Mono.empty()} y es el servicio de dominio quien decide (400). Si el
     * customer-service no está disponible, el fallback responde HTTP 503.
     */
    @Override
    @CircuitBreaker(name = ServiceNames.CUSTOMER_CB, fallbackMethod = "getCustomerByIdFallback")
    @TimeLimiter(name = ServiceNames.CUSTOMER_CB)
    public Mono<CustomerDTO> getCustomerById(String customerId) {
        log.info("Llamando al servicio externo Customer-Service para el ID: {}", customerId);
        return webClientBuilder.build()
                .get()
                .uri(customerServiceUrl + customerId)
                .retrieve()
                .bodyToMono(CustomerDTO.class)
                .onErrorResume(WebClientResponseException.NotFound.class, ex -> Mono.empty());
    }

    private Mono<CustomerDTO> getCustomerByIdFallback(String customerId, Throwable throwable) {
        log.error("customer-service no disponible para el cliente {}: {}", customerId, throwable.toString());
        return ServiceFallbacks.unavailable(ServiceNames.CUSTOMER, throwable);
    }
}
