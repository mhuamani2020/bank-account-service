package com.nttdata.bank_account_service.repository;

import com.nttdata.bank_account_service.model.DebitCard;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repositorio reactivo de la colección {@code debit_cards} de MongoDB.
 * <p>
 * Siguiendo el requisito de usar <b>Spring Data sin consultas SQL dinámicas ni
 * {@code @Query}</b>, todas las consultas se derivan de la firma del método.
 * Esta colección pertenece en exclusiva a {@code bank-account-service}
 * (patrón <i>database per service</i>).
 *
 * @author NTT Data
 */
@Repository
public interface DebitCardRepository extends ReactiveMongoRepository<DebitCard, String> {

    /**
     * Tarjetas de débito de un cliente.
     *
     * @param customerId identificador del cliente titular
     * @return flujo con las tarjetas del cliente
     */
    Flux<DebitCard> findByCustomerId(String customerId);

    /**
     * Indica si una cuenta bancaria ya tiene una tarjeta de débito asociada
     * (regla: una cuenta tiene como máximo una tarjeta de débito).
     *
     * @param accountId identificador de la cuenta
     * @return {@code true} si la cuenta ya tiene una tarjeta
     */
    Mono<Boolean> existsByAccountId(String accountId);
}
