package com.nttdata.bank_account_service.controller;

import com.nttdata.bank_account_service.api.ReportsApi;
import com.nttdata.bank_account_service.dto.ProductReportItem;
import com.nttdata.bank_account_service.dto.TransactionResponse;
import com.nttdata.bank_account_service.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

/**
 * Controlador REST de reportes (Parte II).
 * <p>
 * Implementa la interfaz {@link ReportsApi} generada a partir del contrato OpenAPI
 * ({@code src/main/resources/openapi.yaml}), respetando el enfoque <i>contract-first</i>.
 * <p>
 * Como es una <b>nueva funcionalidad</b>, su reactividad se implementa con <b>RxJava 3</b>
 * en la capa de servicio ({@link ReportService}); en el borde HTTP esos tipos se adaptan a
 * Reactor (tipo declarado por la interfaz generada) con {@code Mono.from(...toFlowable())}
 * y {@code Flux.from(...)}.
 *
 * @author NTT Data
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ReportController implements ReportsApi {

    private final ReportService reportService;

    /**
     * {@code GET /reports/bank-accounts/products-summary} - reporte general por producto en un rango.
     *
     * @param from     fecha inicial del intervalo (inclusive)
     * @param to       fecha final del intervalo (inclusive)
     * @param exchange contexto de la petición
     * @return respuesta 200 con una fila por tipo de producto
     */
    @Override
    public Mono<ResponseEntity<Flux<ProductReportItem>>> getAccountProductsSummary(
            LocalDate from, LocalDate to, ServerWebExchange exchange) {
        log.info("Generating products summary report from {} to {}", from, to);
        return Mono.from(reportService.getAccountProductsSummary(from, to).toFlowable())
                .map(items -> ResponseEntity.ok(Flux.fromIterable(items)));
    }

    /**
     * {@code GET /reports/debit-card/{cardId}/last-movements} — últimos 10 movimientos
     * de una tarjeta de débito.
     *
     * @param cardId   identificador de la tarjeta de débito
     * @param exchange contexto de la petición
     * @return respuesta 200 con los diez movimientos más recientes
     */
    @Override
    public Mono<ResponseEntity<Flux<TransactionResponse>>> getDebitCardLastMovements(
            String cardId, ServerWebExchange exchange) {
        log.info("Fetching last movements of debit card: {}", cardId);
        return Mono.just(ResponseEntity.ok(Flux.from(reportService.getDebitCardLastMovements(cardId))));
    }
}
