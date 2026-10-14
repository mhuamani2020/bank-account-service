package com.nttdata.bank_account_service.controller;

import com.nttdata.bank_account_service.dto.BankAccountRequest;
import com.nttdata.bank_account_service.dto.BankAccountResponse;
import com.nttdata.bank_account_service.dto.TransactionRequest;
import com.nttdata.bank_account_service.dto.TransactionResponse;
import com.nttdata.bank_account_service.dto.TransferRequest;
import com.nttdata.bank_account_service.exception.GlobalExceptionHandler;
import com.nttdata.bank_account_service.service.BankAccountService;
import com.nttdata.bank_account_service.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = BankAccountController.class)
@Import(GlobalExceptionHandler.class)
class BankAccountControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private BankAccountService bankAccountService;

    @MockBean
    private TransactionService transactionService;

    private BankAccountResponse sampleResponse;
    private BankAccountRequest sampleRequest;

    @BeforeEach
    void setUp() {
        sampleResponse = new BankAccountResponse();
        sampleResponse.setId("acc-100");
        sampleResponse.setCustomerId("cust-01");
        sampleResponse.setAccountType("SAVINGS");
        sampleResponse.setBalance(500.0);

        sampleRequest = new BankAccountRequest();
        sampleRequest.setCustomerId("cust-01");
        sampleRequest.setAccountType("SAVINGS");
        sampleRequest.setBalance(500.0);
    }

    // --- ENDPOINTS DE CUENTAS BANCARIAS ---

    @Test
    @DisplayName("GET /bank-accounts: Retorna 200 OK con lista de cuentas")
    void findAll_shouldReturnOkWithAccounts() {
        when(bankAccountService.findAll()).thenReturn(Flux.just(sampleResponse));

        webTestClient.get()
                .uri("/bank-accounts")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(BankAccountResponse.class)
                .hasSize(1)
                .contains(sampleResponse);
    }

    @Test
    @DisplayName("GET /bank-accounts/{id}: Retorna 200 OK cuando existe la cuenta")
    void findById_whenExists_shouldReturnOk() {
        when(bankAccountService.findById("acc-100")).thenReturn(Mono.just(sampleResponse));

        webTestClient.get()
                .uri("/bank-accounts/{id}", "acc-100")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(BankAccountResponse.class)
                .isEqualTo(sampleResponse);
    }

    @Test
    @DisplayName("POST /bank-accounts: Retorna 201 Created al crear cuenta exitosamente")
    void create_shouldReturnCreated() {
        when(bankAccountService.create(any(BankAccountRequest.class))).thenReturn(Mono.just(sampleResponse));

        webTestClient.post()
                .uri("/bank-accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(sampleRequest)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(BankAccountResponse.class)
                .isEqualTo(sampleResponse);
    }

    @Test
    @DisplayName("POST /bank-accounts: Retorna 400 Bad Request cuando el servicio lanza IllegalArgumentException")
    void create_whenBusinessRuleFails_shouldReturnBadRequest() {
        when(bankAccountService.create(any(BankAccountRequest.class)))
                .thenReturn(Mono.error(new IllegalArgumentException("El cliente no existe")));

        webTestClient.post()
                .uri("/bank-accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(sampleRequest)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").isEqualTo("Bad Request")
                .jsonPath("$.message").isEqualTo("El cliente no existe");
    }

    @Test
    @DisplayName("PUT /bank-accounts/{id}: Retorna 200 OK al actualizar")
    void update_shouldReturnOk() {
        when(bankAccountService.update(eq("acc-100"), any(BankAccountRequest.class)))
                .thenReturn(Mono.just(sampleResponse));

        webTestClient.put()
                .uri("/bank-accounts/{id}", "acc-100")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(sampleRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody(BankAccountResponse.class)
                .isEqualTo(sampleResponse);
    }

    @Test
    @DisplayName("DELETE /bank-accounts/{id}: Retorna 204 No Content al eliminar")
    void delete_shouldReturnNoContent() {
        when(bankAccountService.delete("acc-100")).thenReturn(Mono.empty());

        webTestClient.delete()
                .uri("/bank-accounts/{id}", "acc-100")
                .exchange()
                .expectStatus().isNoContent();
    }

    // --- ENDPOINTS DE TRANSACCIONES Y TRANSFERENCIAS ---

    @Test
    @DisplayName("POST /bank-accounts/{accountId}/transactions: Retorna 201 Created")
    void createTransaction_shouldReturnCreated() {
        TransactionRequest txRequest = new TransactionRequest();
        txRequest.setTransactionType("DEPOSIT");
        txRequest.setAmount(100.0);

        TransactionResponse txResponse = new TransactionResponse();
        txResponse.setId("tx-500");
        txResponse.setAccountId("acc-100");
        txResponse.setTransactionType("DEPOSIT");
        txResponse.setAmount(100.0);

        when(transactionService.createTransaction(eq("acc-100"), any(TransactionRequest.class)))
                .thenReturn(Mono.just(txResponse));

        webTestClient.post()
                .uri("/bank-accounts/{accountId}/transactions", "acc-100")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(txRequest)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(TransactionResponse.class)
                .isEqualTo(txResponse);
    }

    @Test
    @DisplayName("GET /bank-accounts/{accountId}/transactions: Retorna 200 OK con historial")
    void getTransactionsByAccount_shouldReturnList() {
        TransactionResponse txResponse = new TransactionResponse();
        txResponse.setId("tx-500");
        txResponse.setAccountId("acc-100");

        when(transactionService.getTransactionsByAccount("acc-100"))
                .thenReturn(Flux.just(txResponse));

        webTestClient.get()
                .uri("/bank-accounts/{accountId}/transactions", "acc-100")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(TransactionResponse.class)
                .hasSize(1);
    }

    @Test
    @DisplayName("POST /bank-accounts/transfers: Retorna 201 Created al completar transferencia")
    void createTransfer_shouldReturnCreated() {
        TransferRequest transferRequest = new TransferRequest();
        transferRequest.setSourceAccountId("acc-100");
        transferRequest.setTargetAccountId("acc-200");
        transferRequest.setAmount(50.0);

        when(transactionService.createTransfer(any(TransferRequest.class)))
                .thenReturn(Mono.empty());

        webTestClient.post()
                .uri("/bank-accounts/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(transferRequest)
                .exchange()
                .expectStatus().isCreated();
    }
}