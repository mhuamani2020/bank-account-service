package com.nttdata.bank_account_service.client;

import reactor.core.publisher.Mono;

public interface CreditClient {
    /**
     * Verifica si un cliente tiene al menos una tarjeta de crédito.
     */
    Mono<Boolean> customerHasCreditCard(String customerId);
}
