package com.nttdata.bank_account_service.repository;

import com.nttdata.bank_account_service.model.BankAccount;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface BankAccountRepository extends ReactiveMongoRepository<BankAccount, String> {
    // Cuenta cuántas cuentas de un tipo específico tiene un cliente
    Mono<Long> countByCustomerIdAndAccountType(String customerId, String accountType);
}
