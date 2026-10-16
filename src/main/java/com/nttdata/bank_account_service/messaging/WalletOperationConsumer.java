package com.nttdata.bank_account_service.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nttdata.bank_account_service.dto.DebitCardPaymentRequest;
import com.nttdata.bank_account_service.dto.TransactionRequest;
import com.nttdata.bank_account_service.enums.TransactionType;
import com.nttdata.bank_account_service.enums.WalletOperationStatus;
import com.nttdata.bank_account_service.enums.WalletOperationType;
import com.nttdata.bank_account_service.event.WalletOperationEvent;
import com.nttdata.bank_account_service.event.WalletOperationResult;
import com.nttdata.bank_account_service.service.BankAccountService;
import com.nttdata.bank_account_service.service.DebitCardService;
import com.nttdata.bank_account_service.service.TransactionService;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;

/**
 * Consumidor de comandos de operación de monedero (Yanki) sobre Kafka (Parte III).
 * <p>
 * Escucha el topic {@code wallet-operations}. Por cada comando recibido aplica el cargo o el
 * abono en la cuenta principal asociada a la tarjeta de débito y publica el resultado en
 * {@code wallet-operation-results}. De este modo el microservicio de monedero (nuevo) nunca
 * invoca la API REST de este servicio: se cumple el requisito
 * <i>"Los nuevos microservicios no podrán invocar las APIs de los microservicios usando
 * peticiones REST"</i>.
 * <p>
 * La lógica se implementa con <b>RxJava 3</b> ({@link Single}); los repositorios y servicios
 * reactivos de Reactor se adaptan en el borde con {@code Single.fromPublisher(...)}.
 * Las reglas de comisión y límite mensual se reutilizan delegando en
 * {@link TransactionService} / {@link DebitCardService}.
 *
 * @author NTT Data
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletOperationConsumer {

    private final DebitCardService debitCardService;
    private final TransactionService transactionService;
    private final BankAccountService bankAccountService;
    private final KafkaEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    /**
     * Procesa un comando de operación de monedero recibido desde Kafka.
     *
     * @param payload evento {@link WalletOperationEvent} serializado en JSON
     */
    @KafkaListener(topics = "${app.kafka.topics.wallet-operations}")
    public void onWalletOperation(String payload) {
        log.info("Received wallet operation event: {}", payload);
        WalletOperationEvent event = deserialize(payload);
        process(event)
                .flatMapCompletable(eventPublisher::publishWalletOperationResult)
                .subscribe(() -> { },
                        error -> log.error("Error processing wallet operation: {}", error.getMessage(), error));
    }

    /**
     * Deserializa el mensaje proveniente de Kafka.
     *
     * @param payload JSON recibido
     * @return evento de operación; si el JSON es inválido devuelve un evento vacío para
     *         responder con un resultado {@code FAILED}
     */
    private WalletOperationEvent deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, WalletOperationEvent.class);
        } catch (Exception ex) {
            log.error("Could not deserialize wallet operation event: {}", ex.getMessage());
            return WalletOperationEvent.builder().build();
        }
    }

    /**
     * Aplica la operación solicitada y devuelve siempre un resultado (nunca propaga error).
     *
     * @param event comando de monedero
     * @return {@code Single} con el resultado de la operación
     */
    private Single<WalletOperationResult> process(WalletOperationEvent event) {
        if (event == null || event.getOperationId() == null || event.getDebitCardId() == null
                || event.getAmount() == null || event.getAmount() <= 0) {
            return Single.just(failed(event,
                    "Operación de monedero inválida: tarjeta e importe positivo son obligatorios"));
        }

        WalletOperationType type;
        try {
            type = WalletOperationType.valueOf(event.getOperationType().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            return Single.just(failed(event,
                    "Tipo de operación de monedero no soportado: " + event.getOperationType()));
        }

        Single<WalletOperationResult> operation = type == WalletOperationType.TOP_UP
                ? topUp(event)
                : withdraw(event);

        return operation.onErrorReturn(error -> failed(event, error.getMessage()));
    }

    /**
     * Carga saldo en el monedero debitando la cuenta asociada a la tarjeta de débito.
     *
     * @param event comando de carga de saldo
     * @return {@code Single} con el resultado
     */
    private Single<WalletOperationResult> topUp(WalletOperationEvent event) {
        DebitCardPaymentRequest request = new DebitCardPaymentRequest();
        request.setAmount(event.getAmount());
        return debitCardService.pay(event.getDebitCardId(), request)
                .map(response -> completed(event, response.getTransactionId(), response.getBalanceAfter()));
    }

    /**
     * Abona (retira) saldo del monedero acreditando la cuenta asociada a la tarjeta de débito.
     *
     * @param event comando de retiro
     * @return {@code Single} con el resultado
     */
    private Single<WalletOperationResult> withdraw(WalletOperationEvent event) {
        return debitCardService.findById(event.getDebitCardId())
                .switchIfEmpty(Single.error(new IllegalArgumentException("La tarjeta de débito no existe")))
                .flatMap(card -> creditAccount(event, card.getAccountId()));
    }

    /**
     * Registra un depósito en la cuenta indicada y compone el resultado con el saldo final.
     *
     * @param event     comando de retiro
     * @param accountId cuenta a acreditar
     * @return {@code Single} con el resultado de la operación
     */
    private Single<WalletOperationResult> creditAccount(WalletOperationEvent event, String accountId) {
        TransactionRequest request = new TransactionRequest();
        request.setTransactionType(TransactionType.DEPOSIT.name());
        request.setAmount(event.getAmount());
        return Single.fromPublisher(transactionService.createTransaction(accountId, request))
                .flatMap(transaction -> Single.fromPublisher(bankAccountService.getBalance(accountId))
                        .map(balance -> completed(event, transaction.getId(), balance.getBalance())));
    }

    /**
     * Construye un resultado exitoso de la operación.
     *
     * @param event         comando original
     * @param transactionId movimiento generado
     * @param balanceAfter  saldo de la cuenta tras la operación
     * @return resultado {@code COMPLETED}
     */
    private WalletOperationResult completed(WalletOperationEvent event, String transactionId, Double balanceAfter) {
        return WalletOperationResult.builder()
                .operationId(event != null ? event.getOperationId() : null)
                .status(WalletOperationStatus.COMPLETED.name())
                .transactionId(transactionId)
                .balanceAfter(balanceAfter)
                .processedAt(Instant.now().toString())
                .build();
    }

    /**
     * Construye un resultado fallido de la operación.
     *
     * @param event   comando original
     * @param message descripción del error
     * @return resultado {@code FAILED}
     */
    private WalletOperationResult failed(WalletOperationEvent event, String message) {
        return WalletOperationResult.builder()
                .operationId(event != null ? event.getOperationId() : null)
                .status(WalletOperationStatus.FAILED.name())
                .errorMessage(message)
                .processedAt(Instant.now().toString())
                .build();
    }
}
