package com.nttdata.bank_account_service.repository;

import com.nttdata.bank_account_service.model.Transaction;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface TransactionRepository extends ReactiveMongoRepository<Transaction, String> {

    // Obtener todo el historial de una cuenta
    Flux<Transaction> findByAccountId(String accountId);

    // Contar cuántos movimientos lleva la cuenta (para calcular la comisión)
    Mono<Long> countByAccountId(String accountId);
}
