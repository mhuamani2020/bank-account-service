package com.nttdata.bank_account_service.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nttdata.bank_account_service.config.KafkaTopicsProperties;
import com.nttdata.bank_account_service.event.AccountTransactionEvent;
import com.nttdata.bank_account_service.event.WalletOperationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de {@link KafkaEventPublisher} con el {@code KafkaTemplate} mockeado
 * (Parte III: los nuevos métodos públicos deben tener sus pruebas con mocks).
 *
 * @author NTT Data
 */
@ExtendWith(MockitoExtension.class)
class KafkaEventPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private KafkaEventPublisher publisher;

    @BeforeEach
    void setUp() {
        KafkaTopicsProperties topics = new KafkaTopicsProperties();
        topics.setAccountTransactions("bank-account-transactions");
        topics.setWalletOperations("wallet-operations");
        topics.setWalletOperationResults("wallet-operation-results");
        publisher = new KafkaEventPublisher(kafkaTemplate, new ObjectMapper(), topics);
    }

    @Test
    @DisplayName("publishAccountTransaction envía el evento al topic de movimientos de cuenta")
    void publishAccountTransaction_shouldSendToTopic() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        AccountTransactionEvent event = AccountTransactionEvent.builder()
                .eventId("e1").accountId("acc-01").customerId("cust-01")
                .transactionType("DEPOSIT").amount(100.0).fee(0.0).balanceAfter(1100.0)
                .transactionId("tx-01").occurredAt("2026-01-01T00:00:00Z")
                .build();

        publisher.publishAccountTransaction(event).test().assertComplete();

        verify(kafkaTemplate).send(eq("bank-account-transactions"), eq("acc-01"), anyString());
    }

    @Test
    @DisplayName("publishWalletOperationResult envía el resultado al topic de resultados de monedero")
    void publishWalletOperationResult_shouldSendToTopic() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        WalletOperationResult result = WalletOperationResult.builder()
                .operationId("op-1").status("COMPLETED").transactionId("tx-1").balanceAfter(900.0)
                .processedAt("2026-01-01T00:00:00Z").build();

        publisher.publishWalletOperationResult(result).test().assertComplete();

        verify(kafkaTemplate).send(eq("wallet-operation-results"), eq("op-1"), anyString());
    }

    @Test
    @DisplayName("Un fallo al publicar no propaga error (publicación best-effort)")
    void publish_whenKafkaFails_shouldCompleteWithoutError() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("broker no disponible"));

        AccountTransactionEvent event = AccountTransactionEvent.builder().accountId("acc-01").build();

        publisher.publishAccountTransaction(event).test().assertComplete();
    }
}
