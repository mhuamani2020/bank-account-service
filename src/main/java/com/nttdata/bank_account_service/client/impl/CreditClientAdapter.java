package com.nttdata.bank_account_service.client.impl;

import com.nttdata.bank_account_service.client.CreditClient;
import com.nttdata.commons.resilience.client.ServiceFallbacks;
import com.nttdata.commons.resilience.client.ServiceNames;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Adaptador reactivo de {@code credit-service} con el estándar de resiliencia del
 * proyecto ({@code bank-resilience-commons}).
 * <p>
 * La URL base no está hardcodeada en el código: se lee de la propiedad externalizada
 * {@code app.urls.credit-service}, definida en los ficheros de configuración
 * ({@code application.yml} local y Config Server).
 *
 * @author NTT Data
 */
@Slf4j
@Component
public class CreditClientAdapter implements CreditClient { // <-- Implementamos la interfaz

    private final WebClient webClient;

    @Value("${app.urls.credit-service}")
    private String creditServiceUrl;

    public CreditClientAdapter(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    /**
     * Consulta si el cliente posee tarjeta de crédito.
     * <p>
     * Estandar de resiliencia: {@link CircuitBreaker} (nombre en {@link ServiceNames#CREDIT_CB})
     * + {@link TimeLimiter} (mismo nombre) para acotar la llamada. Si el servicio no está
     * disponible (timeout, 5xx, CB abierto) el fallback responde con error de disponibilidad
     * (HTTP 503) en lugar de degradar a un falso negativo de negocio.
     */
    @Override
    @CircuitBreaker(name = ServiceNames.CREDIT_CB, fallbackMethod = "customerHasCreditCardFallback")
    @TimeLimiter(name = ServiceNames.CREDIT_CB)
    public Mono<Boolean> customerHasCreditCard(String customerId) {
        log.info("Consultando al credit-service si el cliente {} tiene tarjeta de crédito", customerId);

        return webClient.get()
                .uri(creditServiceUrl + "customer/" + customerId + "/has-credit-card")
                .retrieve()
                .bodyToMono(Boolean.class)
                .switchIfEmpty(ServiceFallbacks.unavailable(ServiceNames.CREDIT,
                        new IllegalStateException("El credit-service devolvió una respuesta vacía")));
    }

    private Mono<Boolean> customerHasCreditCardFallback(String customerId, Throwable throwable) {
        log.error("credit-service no disponible para el cliente {}: {}", customerId, throwable.toString());
        return ServiceFallbacks.unavailable(ServiceNames.CREDIT, throwable);
    }
}
