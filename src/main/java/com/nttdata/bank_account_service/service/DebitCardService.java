package com.nttdata.bank_account_service.service;

import com.nttdata.bank_account_service.dto.DebitCardPaymentRequest;
import com.nttdata.bank_account_service.dto.DebitCardPaymentResponse;
import com.nttdata.bank_account_service.dto.DebitCardRequest;
import com.nttdata.bank_account_service.dto.DebitCardResponse;
import com.nttdata.bank_account_service.dto.TransactionResponse;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * Servicio de tarjetas de débito (Parte III): CRUD de la entidad {@code DebitCard},
 * pagos con la tarjeta y consulta de sus movimientos.
 * <p>
 * Al tratarse de una <b>nueva funcionalidad</b>, la capa de servicio se implementa con el
 * modelo de reactividad de <b>RxJava 3</b> ({@link Single}, {@link Maybe}, {@link Flowable}
 * y {@link Completable}). El controlador, fiel al enfoque <i>contract-first</i>, implementa
 * la interfaz generada desde el contrato OpenAPI y adapta estos tipos a Reactor en el
 * borde HTTP.
 * <p>
 * Una tarjeta de débito se asocia a una cuenta bancaria; los pagos realizados con ella
 * debitan el saldo de esa cuenta y se registran como movimientos de tipo
 * {@code DEBIT_CARD_PAYMENT}.
 *
 * @author NTT Data
 */
public interface DebitCardService {

    /**
     * Crea una tarjeta de débito asociada a una cuenta bancaria existente.
     *
     * @param request cuenta a asociar y estado inicial opcional
     * @return {@code Single} con la tarjeta creada
     */
    Single<DebitCardResponse> create(DebitCardRequest request);

    /**
     * Devuelve todas las tarjetas de débito del sistema (FindAll de la entidad).
     *
     * @return flujo con todas las tarjetas
     */
    Flowable<DebitCardResponse> findAll();

    /**
     * Busca una tarjeta de débito por su identificador.
     *
     * @param id identificador de la tarjeta
     * @return {@code Maybe} con la tarjeta, o vacío si no existe
     */
    Maybe<DebitCardResponse> findById(String id);

    /**
     * Actualiza una tarjeta de débito (por ejemplo, cambiar su estado a BLOCKED).
     *
     * @param id      identificador de la tarjeta
     * @param request nuevos datos de la tarjeta
     * @return {@code Maybe} con la tarjeta actualizada, o vacío si no existe
     */
    Maybe<DebitCardResponse> update(String id, DebitCardRequest request);

    /**
     * Elimina una tarjeta de débito.
     *
     * @param id identificador de la tarjeta
     * @return {@code Completable} que notifica cuándo termina la eliminación
     */
    Completable delete(String id);

    /**
     * Devuelve las tarjetas de débito de un cliente.
     *
     * @param customerId identificador del cliente
     * @return flujo con las tarjetas del cliente
     */
    Flowable<DebitCardResponse> findByCustomer(String customerId);

    /**
     * Realiza un pago con la tarjeta de débito, debitando la cuenta asociada.
     *
     * @param cardId  identificador de la tarjeta
     * @param request importe y descripción del pago
     * @return {@code Single} con el resultado del pago
     */
    Single<DebitCardPaymentResponse> pay(String cardId, DebitCardPaymentRequest request);

    /**
     * Devuelve los movimientos (pagos) realizados con una tarjeta de débito.
     *
     * @param cardId identificador de la tarjeta
     * @return flujo con los movimientos de la tarjeta, del más reciente al más antiguo
     */
    Flowable<TransactionResponse> getMovements(String cardId);
}
