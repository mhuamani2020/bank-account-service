package com.nttdata.bank_account_service.repository;

import com.nttdata.bank_account_service.model.Transaction;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Repositorio reactivo de la colección {@code transactions} de MongoDB.
 * <p>
 * Siguiendo el requisito de usar <b>Spring Data sin consultas SQL dinámicas ni
 * {@code @Query}</b>, todas las consultas se derivan de la firma del método.
 * Esta colección pertenece en exclusiva a {@code bank-account-service}
 * (patrón <i>database per service</i>).
 *
 * @author NTT Data
 */
@Repository
public interface TransactionRepository extends ReactiveMongoRepository<Transaction, String> {

    /**
     * Historial completo de movimientos de una cuenta, en el orden en que se guardaron.
     *
     * @param accountId identificador de la cuenta
     * @return flujo con los movimientos de la cuenta
     */
    Flux<Transaction> findByAccountId(String accountId);

    /**
     * Total de movimientos registrados históricamente para una cuenta.
     *
     * @param accountId identificador de la cuenta
     * @return número de movimientos de la cuenta
     */
    Mono<Long> countByAccountId(String accountId);

    /**
     * Movimientos de una cuenta dentro de un rango de fechas.
     * <p>
     * Es la consulta base del cálculo de <b>comisiones mensuales</b>: permite contar
     * cuántos movimientos se han hecho en el mes en curso y así aplicar el límite
     * mensual de la cuenta de ahorro y el cobro de la comisión de mantenimiento.
     *
     * @param accountId identificador de la cuenta
     * @param from      inicio del rango (inclusive)
     * @param to        fin del rango (exclusive)
     * @return número de movimientos de la cuenta en el rango indicado
     */
    Mono<Long> countByAccountIdAndTransactionDateBetween(String accountId, LocalDateTime from, LocalDateTime to);
}
