package com.nttdata.bank_account_service.service;

import com.nttdata.bank_account_service.dto.BankAccountRequest;
import com.nttdata.bank_account_service.dto.BankAccountResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BankAccountService {
    Flux<BankAccountResponse> findAll();
    Mono<BankAccountResponse> findById(String id);
    Mono<BankAccountResponse> create(BankAccountRequest request);
    Mono<BankAccountResponse> update(String id, BankAccountRequest request);
    Mono<Void> delete(String id);
}
