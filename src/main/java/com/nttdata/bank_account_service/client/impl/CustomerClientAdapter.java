package com.nttdata.bank_account_service.client.impl;

import com.nttdata.bank_account_service.client.CustomerClient;
import com.nttdata.bank_account_service.dto.CustomerDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerClientAdapter implements CustomerClient {
    private final WebClient.Builder webClientBuilder;

    @Value("${app.urls.customer-service}")
    private String customerServiceUrl;

    @Override
    public Mono<CustomerDTO> getCustomerById(String customerId) {
        log.info("Llamando al servicio externo Customer-Service para el ID: {}", customerId);
        return webClientBuilder.build()
                .get()
                .uri(customerServiceUrl + customerId)
                .retrieve()
                .bodyToMono(CustomerDTO.class)
                .onErrorResume(org.springframework.web.reactive.function.client.WebClientResponseException.NotFound.class,
                        ex -> Mono.error(new IllegalArgumentException("El cliente con ID " + customerId + " no existe")))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("El cliente con ID " + customerId + " no existe")));
    }
}
