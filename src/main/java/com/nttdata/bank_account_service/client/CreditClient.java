package com.nttdata.bank_account_service.client;

import reactor.core.publisher.Mono;

/**
 * Cliente reactivo hacia {@code credit-service}.
 * <p>
 * Se usa para validar que un cliente VIP/PYME posea tarjeta de crédito antes de abrir la
 * cuenta, sin romper el patrón <i>database per service</i>: la consulta se hace por HTTP,
 * nunca se accede a la colección de créditos.
 *
 * @author NTT Data
 */
public interface CreditClient {

    /**
     * Verifica si un cliente tiene al menos una tarjeta de crédito.
     *
     * @param customerId identificador del cliente
     * @return {@code Mono} con {@code true} cuando el cliente posee tarjeta de crédito
     */
    Mono<Boolean> customerHasCreditCard(String customerId);
}
