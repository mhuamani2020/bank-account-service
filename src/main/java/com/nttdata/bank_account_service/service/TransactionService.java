package com.nttdata.bank_account_service.service;

import com.nttdata.bank_account_service.dto.TransactionRequest;
import com.nttdata.bank_account_service.dto.TransactionResponse;
import com.nttdata.bank_account_service.dto.TransferRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Servicio de movimientos (depósitos, retiros y transferencias) de las cuentas bancarias.
 * <p>
 * Expone el CRUD completo de la entidad {@code Transaction} exigido por el proyecto
 * (Create, FindAll, Update y Delete) además de las operaciones de negocio de
 * depósito/retiro y transferencia entre cuentas.
 *
 * @author NTT Data
 */
public interface TransactionService {

    /**
     * Registra un depósito o un retiro en la cuenta indicada, aplicando las reglas de
     * comisión del producto (límite mensual de movimientos y comisión de mantenimiento)
     * y las restricciones propias de las cuentas a plazo fijo.
     *
     * @param accountId identificador de la cuenta que recibe el movimiento
     * @param request   tipo y monto del movimiento
     * @return {@code Mono} con el movimiento registrado
     */
    Mono<TransactionResponse> createTransaction(String accountId, TransactionRequest request);

    /**
     * Devuelve el historial de movimientos de una cuenta concreta.
     *
     * @param accountId identificador de la cuenta
     * @return flujo con los movimientos de la cuenta
     */
    Flux<TransactionResponse> getTransactionsByAccount(String accountId);

    /**
     * Ejecuta una transferencia entre dos cuentas: primero retira del origen y después
     * deposita en el destino.
     *
     * @param request cuenta origen, cuenta destino e importe
     * @return {@code Mono} vacío cuando la transferencia ha terminado
     */
    Mono<Void> createTransfer(TransferRequest request);

    /**
     * Devuelve todos los movimientos registrados en el sistema (FindAll de la entidad).
     *
     * @return flujo con todos los movimientos
     */
    Flux<TransactionResponse> findAll();

    /**
     * Busca un movimiento por su identificador (FindById de la entidad).
     *
     * @param id identificador del movimiento
     * @return {@code Mono} con el movimiento, o vacío si no existe
     */
    Mono<TransactionResponse> findById(String id);

    /**
     * Corrige los datos de un movimiento ya registrado (Update de la entidad).
     * <p>
     * Operación administrativa de corrección del registro: no modifica el saldo de la
     * cuenta, que solo cambia a través de nuevos movimientos.
     *
     * @param id      identificador del movimiento
     * @param request nuevos tipo e importe del movimiento
     * @return {@code Mono} con el movimiento corregido, o vacío si no existe
     */
    Mono<TransactionResponse> update(String id, TransactionRequest request);

    /**
     * Elimina un movimiento registrado (Delete de la entidad).
     *
     * @param id identificador del movimiento
     * @return {@code Mono} vacío cuando la eliminación termina
     */
    Mono<Void> delete(String id);
}
