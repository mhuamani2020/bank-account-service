package com.nttdata.bank_account_service.client.impl;

import com.nttdata.bank_account_service.client.CreditClient;
import com.nttdata.commons.resilience.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica de extremo a extremo el estándar de resiliencia sobre {@code CreditClient}:
 * <ul>
 *   <li>el aspecto {@code @CircuitBreaker}/{@code @TimeLimiter} realmente intercepta la llamada,</li>
 *   <li>ante un fallo de disponibilidad el fallback responde {@link ServiceUnavailableException} (HTTP 503),</li>
 *   <li>el circuito se abre al superar el umbral configurado ({@code minimumNumberOfCalls} / {@code failureRateThreshold}).</li>
 * </ul>
 * El destino se apunta a un puerto cerrado para provocar el fallo de forma determinista y sin
 * depender de infraestructura externa.
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.config.enabled=false",
        "spring.data.mongodb.auto-index-creation=false",
        "app.urls.customer-service=http://localhost:9/customers/",
        "app.urls.credit-service=http://localhost:9/credits/",
        "app.jwt.secret=bank-system-super-secret-key-for-jwt-hs256-2026-nttdata",
        // Kafka (Parte III): en esta prueba no hay broker; no arrancamos listeners ni
        // intentamos crear topics automáticamente.
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false"
})
class CircuitBreakerIntegrationTest {

    private static final String CIRCUIT_NAME = "creditServiceCB";

    @Autowired
    private CreditClient creditClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Test
    @DisplayName("El fallback responde 503 y el circuito se abre tras superar el umbral")
    void fallbackReturnsServiceUnavailableAndCircuitOpens() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(CIRCUIT_NAME);
        circuitBreaker.reset();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // mínimo de llamadas configurado (5) y todas fallan -> el circuito debe abrirse
        for (int i = 0; i < 5; i++) {
            StepVerifier.create(creditClient.customerHasCreditCard("cust-" + i))
                    .expectError(ServiceUnavailableException.class)
                    .verify();
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // con el circuito abierto la llamada se cortocircuita y sigue devolviendo 503
        StepVerifier.create(creditClient.customerHasCreditCard("cust-open"))
                .expectError(ServiceUnavailableException.class)
                .verify();
    }

    @Test
    @DisplayName("El fallback de la consulta de deuda vencida responde 503")
    void overdueDebtFallbackReturnsServiceUnavailable() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(CIRCUIT_NAME);
        circuitBreaker.reset();

        // Con el servicio de créditos caído la consulta de deuda vencida degrada a 503
        StepVerifier.create(creditClient.customerHasOverdueDebt("cust-01"))
                .expectError(ServiceUnavailableException.class)
                .verify();
    }
}
