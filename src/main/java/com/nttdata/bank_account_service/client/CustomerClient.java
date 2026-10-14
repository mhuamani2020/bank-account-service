package com.nttdata.bank_account_service.client;

import com.nttdata.bank_account_service.dto.CustomerDTO;
import reactor.core.publisher.Mono;

public interface CustomerClient {
    Mono<CustomerDTO> getCustomerById(String customerId);
}
