package com.nttdata.bank_account_service.service;

import com.nttdata.bank_account_service.dto.TransactionRequest;
import com.nttdata.bank_account_service.dto.TransactionResponse;
import com.nttdata.bank_account_service.dto.TransferRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TransactionService {
    Mono<TransactionResponse> createTransaction(String accountId, TransactionRequest request);
    Flux<TransactionResponse> getTransactionsByAccount(String accountId);
    Mono<Void> createTransfer(TransferRequest request);
}