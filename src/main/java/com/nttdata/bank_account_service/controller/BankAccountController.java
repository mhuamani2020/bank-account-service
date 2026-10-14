package com.nttdata.bank_account_service.controller;

import com.nttdata.bank_account_service.api.BankAccountsApi;
import com.nttdata.bank_account_service.dto.*;
import com.nttdata.bank_account_service.service.BankAccountService;
import com.nttdata.bank_account_service.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequiredArgsConstructor
public class BankAccountController implements BankAccountsApi {

    private final BankAccountService bankAccountService;
    private final TransactionService transactionService; // 1. Inyectamos el servicio

    @Override
    public Mono<ResponseEntity<Flux<BankAccountResponse>>> findAll(ServerWebExchange exchange) {
        log.info("Fetching all bank accounts");
        return Mono.just(ResponseEntity.ok(bankAccountService.findAll()));
    }

    @Override
    public Mono<ResponseEntity<BankAccountResponse>> create(Mono<BankAccountRequest> bankAccountRequest, ServerWebExchange exchange) {
        log.info("Creating new bank account");
        return bankAccountRequest
                .flatMap(bankAccountService::create)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
    }

    @Override
    public Mono<ResponseEntity<BankAccountResponse>> findById(String id, ServerWebExchange exchange) {
        log.info("Fetching bank account by id: {}", id);
        return bankAccountService.findById(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Override
    public Mono<ResponseEntity<BankAccountResponse>> update(String id, Mono<BankAccountRequest> bankAccountRequest, ServerWebExchange exchange) {
        log.info("Updating bank account by id: {}", id);
        return bankAccountRequest
                .flatMap(request -> bankAccountService.update(id, request))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Override
    public Mono<ResponseEntity<Void>> delete(String id, ServerWebExchange exchange) {
        log.info("Deleting bank account by id: {}", id);
        return bankAccountService.delete(id)
                .then(Mono.just(new ResponseEntity<Void>(HttpStatus.NO_CONTENT)));
    }

    @Override
    public Mono<ResponseEntity<TransactionResponse>> createTransaction(String accountId, Mono<TransactionRequest> transactionRequest, ServerWebExchange exchange) {
        log.info("Creando transacción para la cuenta: {}", accountId);
        return transactionRequest
                .flatMap(request -> transactionService.createTransaction(accountId, request))
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
    }

    @Override
    public Mono<ResponseEntity<Flux<TransactionResponse>>> getTransactionsByAccount(String accountId, ServerWebExchange exchange) {
        log.info("Obteniendo historial de transacciones para la cuenta: {}", accountId);
        Flux<TransactionResponse> transactions = transactionService.getTransactionsByAccount(accountId);
        return Mono.just(ResponseEntity.ok(transactions));
    }

    @Override
    public Mono<ResponseEntity<Void>> createTransfer(Mono<TransferRequest> transferRequest, ServerWebExchange exchange) {
        log.info("Procesando solicitud de transferencia");
        return transferRequest
                .flatMap(transactionService::createTransfer)
                .thenReturn(ResponseEntity.status(HttpStatus.CREATED).build());
    }
}
