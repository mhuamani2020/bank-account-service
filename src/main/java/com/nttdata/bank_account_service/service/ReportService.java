package com.nttdata.bank_account_service.service;

import com.nttdata.bank_account_service.dto.ProductReportItem;
import com.nttdata.bank_account_service.dto.TransactionResponse;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

import java.time.LocalDate;
import java.util.List;

/**
 * Servicio de reportes de productos de cuenta (Parte II).
 * <p>
 * Agrupa los productos del banco (tipos de cuenta) y sus movimientos en un intervalo de
 * tiempo solicitado por el usuario, y expone el reporte de los últimos movimientos de una
 * tarjeta de débito.
 * <p>
 * Al tratarse de una <b>nueva funcionalidad</b>, la capa de servicio se implementa con el
 * modelo de reactividad de <b>RxJava 3</b> ({@link Single} / {@link Flowable}); el
 * controlador, fiel al enfoque <i>contract-first</i>, implementa la interfaz generada
 * desde el contrato OpenAPI y adapta estos tipos a Reactor en el borde HTTP.
 *
 * @author NTT Data
 */
public interface ReportService {

    /**
     * Genera el reporte general por producto (tipo de cuenta) en el intervalo indicado.
     *
     * @param from fecha inicial del intervalo (inclusive)
     * @param to   fecha final del intervalo (inclusive)
     * @return {@code Single} con una fila de reporte por cada tipo de cuenta
     */
    Single<List<ProductReportItem>> getAccountProductsSummary(LocalDate from, LocalDate to);

    /**
     * Devuelve los últimos diez movimientos de una tarjeta de débito.
     *
     * @param cardId identificador de la tarjeta de débito
     * @return flujo con los diez movimientos más recientes de la tarjeta
     */
    Flowable<TransactionResponse> getDebitCardLastMovements(String cardId);
}
