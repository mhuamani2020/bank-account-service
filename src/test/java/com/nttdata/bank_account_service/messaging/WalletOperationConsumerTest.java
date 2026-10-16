package com.nttdata.bank_account_service.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nttdata.bank_account_service.dto.AvailableBalanceResponse;
import com.nttdata.bank_account_service.dto.DebitCardPaymentRequest;
import com.nttdata.bank_account_service.dto.DebitCardPaymentResponse;
import com.nttdata.bank_account_service.dto.DebitCardResponse;
import com.nttdata.bank_account_service.dto.TransactionRequest;
import com.nttdata.bank_account_service.dto.TransactionResponse;
import com.nttdata.bank_account_service.event.WalletOperationEvent;
import com.nttdata.bank_account_service.event.WalletOperationResult;
import com.nttdata.bank_account_service.service.BankAccountService;
import com.nttdata.bank_account_service.service.DebitCardService;
import com.nttdata.bank_account_service.service.TransactionService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de {@link WalletOperationConsumer} con todos sus colaboradores mockeados
 * (Parte III: los nuevos métodos públicos deben tener sus pruebas con mocks).
 * <p>
 * Se verifican los dos flujos de operación de monedero (carga y retiro), la traducción de
 * errores de negocio a un resultado {@code FAILED} y la validación de comandos inválidos.
 *
 * @author NTT Data
 */
class WalletOperationConsumerTest {

    private DebitCardService debitCardService;
    private TransactionService transactionService;
    private BankAccountService bankAccountService;
    private KafkaEventPublisher eventPublisher;
    private WalletOperationConsumer consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        debitCardService = mock(DebitCardService.class);
        transactionService = mock(TransactionService.class);
        bankAccountService = mock(BankAccountService.class);
        eventPublisher = mock(KafkaEventPublisher.class);
        consumer = new WalletOperationConsumer(debitCardService, transactionService, bankAccountService,
                eventPublisher, objectMapper);
        lenient().when(eventPublisher.publishWalletOperationResult(any())).thenReturn(Completable.complete());
    }

    @Test
    @DisplayName("TOP_UP: debita la cuenta de la tarjeta y publica un resultado COMPLETED")
    void topUp_shouldDebitAccountAndPublishCompleted() {
        DebitCardPaymentResponse payment = new DebitCardPaymentResponse();
        payment.setCardId("card-01");
        payment.setTransactionId("tx-01");
        payment.setBalanceAfter(400.0);
        when(debitCardService.pay(eq("card-01"), any(DebitCardPaymentRequest.class))).thenReturn(Single.just(payment));

        consumer.onWalletOperation(json("op-1", "TOP_UP", "card-01", 100.0));

        WalletOperationResult result = capturePublishedResult();
        assertThat(result.getOperationId()).isEqualTo("op-1");
        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getTransactionId()).isEqualTo("tx-01");
        assertThat(result.getBalanceAfter()).isEqualTo(400.0);
    }

    @Test
    @DisplayName("WITHDRAW: acredita la cuenta de la tarjeta y publica un resultado COMPLETED")
    void withdraw_shouldCreditAccountAndPublishCompleted() {
        DebitCardResponse card = new DebitCardResponse();
        card.setId("card-01");
        card.setAccountId("acc-01");

        TransactionResponse transaction = new TransactionResponse();
        transaction.setId("tx-02");
        transaction.setAccountId("acc-01");

        AvailableBalanceResponse balance = new AvailableBalanceResponse();
        balance.setAccountId("acc-01");
        balance.setBalance(650.0);

        when(debitCardService.findById("card-01")).thenReturn(Maybe.just(card));
        when(transactionService.createTransaction(eq("acc-01"), any(TransactionRequest.class)))
                .thenReturn(Mono.just(transaction));
        when(bankAccountService.getBalance("acc-01")).thenReturn(Mono.just(balance));

        consumer.onWalletOperation(json("op-2", "WITHDRAW", "card-01", 50.0));

        WalletOperationResult result = capturePublishedResult();
        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getTransactionId()).isEqualTo("tx-02");
        assertThat(result.getBalanceAfter()).isEqualTo(650.0);

        ArgumentCaptor<TransactionRequest> requestCaptor = ArgumentCaptor.forClass(TransactionRequest.class);
        verify(transactionService).createTransaction(eq("acc-01"), requestCaptor.capture());
        assertThat(requestCaptor.getValue().getTransactionType()).isEqualTo("DEPOSIT");
        assertThat(requestCaptor.getValue().getAmount()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("Comando inválido (importe no positivo): publica FAILED sin tocar la tarjeta")
    void invalidAmount_shouldPublishFailedWithoutSideEffects() {
        consumer.onWalletOperation(json("op-3", "TOP_UP", "card-01", 0.0));

        WalletOperationResult result = capturePublishedResult();
        assertThat(result.getStatus()).isEqualTo("FAILED");
        verifyNoInteractions(debitCardService);
    }

    @Test
    @DisplayName("Tarjeta inexistente en un retiro: publica FAILED")
    void withdrawWithUnknownCard_shouldPublishFailed() {
        when(debitCardService.findById("card-99")).thenReturn(Maybe.empty());

        consumer.onWalletOperation(json("op-4", "WITHDRAW", "card-99", 20.0));

        WalletOperationResult result = capturePublishedResult();
        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getErrorMessage()).contains("no existe");
    }

    @Test
    @DisplayName("Tipo de operación no soportado: publica FAILED")
    void unsupportedType_shouldPublishFailed() {
        consumer.onWalletOperation(json("op-5", "TRANSFER", "card-01", 10.0));

        WalletOperationResult result = capturePublishedResult();
        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getErrorMessage()).contains("no soportado");
    }

    @Test
    @DisplayName("Error de negocio del pago (saldo insuficiente): publica FAILED")
    void topUpWithBusinessError_shouldPublishFailed() {
        when(debitCardService.pay(eq("card-01"), any(DebitCardPaymentRequest.class)))
                .thenReturn(Single.error(new IllegalArgumentException("Saldo insuficiente")));

        consumer.onWalletOperation(json("op-6", "TOP_UP", "card-01", 9999.0));

        WalletOperationResult result = capturePublishedResult();
        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getErrorMessage()).contains("Saldo insuficiente");
    }

    @Test
    @DisplayName("JSON inválido: publica FAILED (no propaga excepción)")
    void invalidJson_shouldPublishFailed() {
        consumer.onWalletOperation("{no-es-json}");

        WalletOperationResult result = capturePublishedResult();
        assertThat(result.getStatus()).isEqualTo("FAILED");
        verifyNoInteractions(debitCardService);
    }

    /**
     * Captura el resultado publicado por el consumidor.
     *
     * @return resultado {@link WalletOperationResult} enviado al publicador
     */
    private WalletOperationResult capturePublishedResult() {
        ArgumentCaptor<WalletOperationResult> captor = ArgumentCaptor.forClass(WalletOperationResult.class);
        verify(eventPublisher).publishWalletOperationResult(captor.capture());
        return captor.getValue();
    }

    /**
     * Serializa un comando de operación de monedero a JSON.
     *
     * @param operationId identificador de la operación
     * @param type        tipo de operación
     * @param cardId      tarjeta de débito
     * @param amount      importe
     * @return JSON del evento
     */
    private String json(String operationId, String type, String cardId, Double amount) {
        WalletOperationEvent event = WalletOperationEvent.builder()
                .operationId(operationId)
                .operationType(type)
                .debitCardId(cardId)
                .amount(amount)
                .requestedAt("2026-01-01T00:00:00Z")
                .build();
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
