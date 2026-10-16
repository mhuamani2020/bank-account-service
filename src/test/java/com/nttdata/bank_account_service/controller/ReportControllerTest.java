package com.nttdata.bank_account_service.controller;

import com.nttdata.bank_account_service.config.TestSecurityConfig;
import com.nttdata.bank_account_service.dto.ProductReportItem;
import com.nttdata.bank_account_service.dto.TransactionResponse;
import com.nttdata.bank_account_service.service.ReportService;
import com.nttdata.commons.resilience.error.StandardApiExceptionHandler;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Pruebas del controlador de reportes de {@code bank-account-service}, que implementa la
 * interfaz {@code ReportsApi} generada desde el contrato OpenAPI (<i>contract-first</i>).
 * El servicio se mockea devolviendo tipos de <b>RxJava 3</b>, que el controlador adapta a
 * Reactor en el borde HTTP.
 *
 * @author NTT Data
 */
@WebFluxTest(controllers = ReportController.class)
@Import({StandardApiExceptionHandler.class, TestSecurityConfig.class})
class ReportControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ReportService reportService;

    @Test
    @DisplayName("GET /reports/bank-accounts/products-summary: Retorna 200 OK con las filas del reporte")
    void getProductsSummary_shouldReturnOk() {
        ProductReportItem item = new ProductReportItem();
        item.setProductType("SAVINGS");
        item.setNumberOfProducts(2L);
        item.setTotalBalance(400.0);
        item.setNumberOfMovements(3L);
        item.setTotalMovementAmount(120.0);

        when(reportService.getAccountProductsSummary(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Single.just(List.of(item)));

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/reports/bank-accounts/products-summary")
                        .queryParam("from", "2026-01-01")
                        .queryParam("to", "2026-01-31")
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ProductReportItem.class)
                .hasSize(1)
                .contains(item);
    }

    @Test
    @DisplayName("GET /reports/bank-accounts/products-summary: Retorna 400 si el rango de fechas es inválido")
    void getProductsSummary_whenInvalidRange_shouldReturnBadRequest() {
        when(reportService.getAccountProductsSummary(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Single.error(new IllegalArgumentException("La fecha 'from' no puede ser posterior a 'to'")));

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/reports/bank-accounts/products-summary")
                        .queryParam("from", "2026-02-01")
                        .queryParam("to", "2026-01-01")
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").isEqualTo("La fecha 'from' no puede ser posterior a 'to'");
    }

    @Test
    @DisplayName("GET /reports/debit-card/{cardId}/last-movements: Retorna 200 OK con los movimientos")
    void getDebitCardLastMovements_shouldReturnOk() {
        TransactionResponse movement = new TransactionResponse();
        movement.setId("tx-1");
        movement.setAccountId("acc-1");
        movement.setDebitCardId("card-9");
        movement.setAmount(80.0);

        when(reportService.getDebitCardLastMovements("card-9")).thenReturn(Flowable.just(movement));

        webTestClient.get()
                .uri("/reports/debit-card/{cardId}/last-movements", "card-9")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(TransactionResponse.class)
                .hasSize(1)
                .contains(movement);
    }
}
