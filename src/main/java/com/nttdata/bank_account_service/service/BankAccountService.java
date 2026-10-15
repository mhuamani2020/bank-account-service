package com.nttdata.bank_account_service.service;

import com.nttdata.bank_account_service.dto.AvailableBalanceResponse;
import com.nttdata.bank_account_service.dto.BankAccountRequest;
import com.nttdata.bank_account_service.dto.BankAccountResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Servicio de cuentas bancarias (Pasivos) del sistema.
 * <p>
 * Expone el CRUD completo de la entidad {@code BankAccount} y delega en la capa de
 * implementación la validación de las reglas de asignación de productos y de las
 * reglas de producto por tipo de cuenta.
 *
 * @author NTT Data
 */
public interface BankAccountService {

    /**
     * Devuelve todas las cuentas bancarias del sistema (FindAll de la entidad).
     *
     * @return flujo con todas las cuentas
     */
    Flux<BankAccountResponse> findAll();

    /**
     * Busca una cuenta bancaria por su identificador.
     *
     * @param id identificador de la cuenta
     * @return {@code Mono} con la cuenta, o vacío si no existe
     */
    Mono<BankAccountResponse> findById(String id);

    /**
     * Consulta el saldo disponible de una cuenta bancaria (Parte I: "consultar los
     * saldos disponibles en sus productos").
     * <p>
     * Es la contrapartida, para cuentas, del endpoint de saldo disponible de
     * {@code credit-service} para tarjetas de crédito.
     *
     * @param id identificador de la cuenta
     * @return {@code Mono} con el saldo disponible, o vacío si la cuenta no existe
     */
    Mono<AvailableBalanceResponse> getBalance(String id);

    /**
     * Crea una cuenta bancaria validando que el cliente exista, que pueda contratar el
     * producto y que la cuenta cumpla las reglas de producto de su tipo.
     *
     * @param request datos de la cuenta a crear
     * @return {@code Mono} con la cuenta creada
     */
    Mono<BankAccountResponse> create(BankAccountRequest request);

    /**
     * Reemplaza una cuenta existente revalidando sus reglas de producto (semántica PUT).
     *
     * @param id      identificador de la cuenta
     * @param request nuevos datos de la cuenta
     * @return {@code Mono} con la cuenta actualizada, o vacío si el ID no existe
     */
    Mono<BankAccountResponse> update(String id, BankAccountRequest request);

    /**
     * Elimina una cuenta bancaria por su identificador.
     *
     * @param id identificador de la cuenta
     * @return {@code Mono} vacío cuando la eliminación termina
     */
    Mono<Void> delete(String id);
}
