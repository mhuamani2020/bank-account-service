package com.nttdata.bank_account_service.controller;

import com.nttdata.bank_account_service.config.TestSecurityConfig;
import com.nttdata.bank_account_service.dto.DebitCardPaymentRequest;
import com.nttdata.bank_account_service.dto.DebitCardPaymentResponse;
import com.nttdata.bank_account_service.dto.DebitCardRequest;
import com.nttdata.bank_account_service.dto.DebitCardResponse;
import com.nttdata.bank_account_service.dto.TransactionResponse;
import com.nttdata.bank_account_service.service.DebitCardService;
import com.nttdata.commons.resilience.error.StandardApiExceptionHandler;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Pruebas del controlador de tarjetas de débito, que implementa la interfaz
 * {@code DebitCardsApi} generada a partir del contrato OpenAPI (<i>contract-first</i>).
 * <p>
 * El servicio se mockea devolviendo los tipos de <b>RxJava 3</b> ({@code Single} /
 * {@code Maybe} / {@code Flowable} / {@code Completable}) que produce la nueva
 * funcionalidad; se verifica que el controlador los adapta a Reactor y que las reglas de
 * negocio se traducen a los códigos HTTP esperados.
 *
 * @author NTT Data
 */
@WebFluxTest(controllers = DebitCardController.class)
@Import({StandardApiExceptionHandler.class, TestSecurityConfig.class})
class DebitCardControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private DebitCardService debitCardService;

    private DebitCardResponse sampleResponse;
    private DebitCardRequest sampleRequest;

    @BeforeEach
    void setUp() {
        sampleResponse = new DebitCardResponse();
        sampleResponse.setId("card-01");
        sampleResponse.setAccountId("acc-100");
        sampleResponse.setCustomerId("cust-01");
        sampleResponse.setCardNumber("4123456789012345");
        sampleResponse.setStatus("ACTIVE");

        sampleRequest = new DebitCardRequest();
        sampleRequest.setAccountId("acc-100");
    }

    @Test
    @DisplayName("GET /debit-cards: Retorna 200 OK con la lista de tarjetas")
    void findAll_shouldReturnOk() {
        when(debitCardService.findAll()).thenReturn(Flowable.just(sampleResponse));

        webTestClient.get()
                .uri("/debit-cards")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(DebitCardResponse.class)
                .hasSize(1)
                .contains(sampleResponse);
    }

    @Test
    @DisplayName("POST /debit-cards: Retorna 201 Created al emitir la tarjeta")
    void create_shouldReturnCreated() {
        when(debitCardService.create(any(DebitCardRequest.class))).thenReturn(Single.just(sampleResponse));

        webTestClient.post()
                .uri("/debit-cards")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(sampleRequest)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(DebitCardResponse.class)
                .isEqualTo(sampleResponse);
    }

    @Test
    @DisplayName("POST /debit-cards: Retorna 400 cuando falla una regla de negocio")
    void create_whenBusinessRuleFails_shouldReturnBadRequest() {
        when(debitCardService.create(any(DebitCardRequest.class)))
                .thenReturn(Single.error(new IllegalArgumentException("La cuenta bancaria no existe")));

        webTestClient.post()
                .uri("/debit-cards")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(sampleRequest)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").isEqualTo("La cuenta bancaria no existe");
    }

    @Test
    @DisplayName("GET /debit-cards/{id}: Retorna 200 OK cuando existe")
    void findById_whenExists_shouldReturnOk() {
        when(debitCardService.findById("card-01")).thenReturn(Maybe.just(sampleResponse));

        webTestClient.get()
                .uri("/debit-cards/{id}", "card-01")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DebitCardResponse.class)
                .isEqualTo(sampleResponse);
    }

    @Test
    @DisplayName("GET /debit-cards/{id}: Retorna 404 si no existe")
    void findById_whenNotFound_shouldReturnNotFound() {
        when(debitCardService.findById("card-999")).thenReturn(Maybe.empty());

        webTestClient.get()
                .uri("/debit-cards/{id}", "card-999")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("PUT /debit-cards/{id}: Retorna 200 OK al actualizar")
    void update_shouldReturnOk() {
        when(debitCardService.update(eq("card-01"), any(DebitCardRequest.class)))
                .thenReturn(Maybe.just(sampleResponse));

        webTestClient.put()
                .uri("/debit-cards/{id}", "card-01")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(sampleRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DebitCardResponse.class)
                .isEqualTo(sampleResponse);
    }

    @Test
    @DisplayName("DELETE /debit-cards/{id}: Retorna 204 No Content")
    void delete_shouldReturnNoContent() {
        when(debitCardService.delete("card-01")).thenReturn(Completable.complete());

        webTestClient.delete()
                .uri("/debit-cards/{id}", "card-01")
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    @DisplayName("GET /debit-cards/customer/{customerId}: Retorna 200 OK con las tarjetas del cliente")
    void findByCustomer_shouldReturnOk() {
        when(debitCardService.findByCustomer("cust-01")).thenReturn(Flowable.just(sampleResponse));

        webTestClient.get()
                .uri("/debit-cards/customer/{customerId}", "cust-01")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(DebitCardResponse.class)
                .hasSize(1);
    }

    @Test
    @DisplayName("POST /debit-cards/{id}/payments: Retorna 201 Created al pagar")
    void pay_shouldReturnCreated() {
        DebitCardPaymentRequest paymentRequest = new DebitCardPaymentRequest();
        paymentRequest.setAmount(100.0);

        DebitCardPaymentResponse paymentResponse = new DebitCardPaymentResponse();
        paymentResponse.setCardId("card-01");
        paymentResponse.setAccountId("acc-100");
        paymentResponse.setTransactionId("tx-01");
        paymentResponse.setAmount(100.0);
        paymentResponse.setFee(0.0);
        paymentResponse.setBalanceAfter(400.0);

        when(debitCardService.pay(eq("card-01"), any(DebitCardPaymentRequest.class)))
                .thenReturn(Single.just(paymentResponse));

        webTestClient.post()
                .uri("/debit-cards/{id}/payments", "card-01")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(paymentRequest)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(DebitCardPaymentResponse.class)
                .isEqualTo(paymentResponse);
    }

    @Test
    @DisplayName("POST /debit-cards/{id}/payments: Retorna 400 si la tarjeta está bloqueada")
    void pay_whenCardBlocked_shouldReturnBadRequest() {
        DebitCardPaymentRequest paymentRequest = new DebitCardPaymentRequest();
        paymentRequest.setAmount(100.0);

        when(debitCardService.pay(eq("card-01"), any(DebitCardPaymentRequest.class)))
                .thenReturn(Single.error(new IllegalArgumentException("La tarjeta de débito no está activa para realizar pagos")));

        webTestClient.post()
                .uri("/debit-cards/{id}/payments", "card-01")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(paymentRequest)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("GET /debit-cards/{id}/movements: Retorna 200 OK con los movimientos")
    void getMovements_shouldReturnOk() {
        TransactionResponse transaction = new TransactionResponse();
        transaction.setId("tx-01");
        transaction.setAccountId("acc-100");
        transaction.setTransactionType("DEBIT_CARD_PAYMENT");
        transaction.setAmount(100.0);
        transaction.setDebitCardId("card-01");

        when(debitCardService.getMovements("card-01")).thenReturn(Flowable.just(transaction));

        webTestClient.get()
                .uri("/debit-cards/{id}/movements", "card-01")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(TransactionResponse.class)
                .hasSize(1)
                .contains(transaction);
    }
}
