package com.nttdata.bank_account_service.repository;

import com.nttdata.bank_account_service.model.BankAccount;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Repositorio reactivo de la colección {@code bank_accounts} de MongoDB.
 * <p>
 * Al igual que en el resto de servicios, solo se usan consultas derivadas de Spring Data,
 * sin {@code @Query} ni SQL dinámico. Esta colección pertenece en exclusiva a
 * {@code bank-account-service} (patrón <i>database per service</i>).
 *
 * @author NTT Data
 */
@Repository
public interface BankAccountRepository extends ReactiveMongoRepository<BankAccount, String> {

    /**
     * Cuenta cuántas cuentas de un tipo específico tiene un cliente
     * (regla: un cliente personal solo puede tener una cuenta de cada tipo).
     *
     * @param customerId  identificador del cliente
     * @param accountType tipo de cuenta en formato {@code name()} del enum
     * @return número de cuentas que coinciden
     */
    Mono<Long> countByCustomerIdAndAccountType(String customerId, String accountType);
}
