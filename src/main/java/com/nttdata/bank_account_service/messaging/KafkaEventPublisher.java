package com.nttdata.bank_account_service.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nttdata.bank_account_service.config.KafkaTopicsProperties;
import com.nttdata.bank_account_service.event.AccountTransactionEvent;
import com.nttdata.bank_account_service.event.WalletOperationResult;
import io.reactivex.rxjava3.core.Completable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publicador de eventos de dominio de {@code bank-account-service} hacia Kafka (Parte III).
 * <p>
 * Traduce los eventos de negocio a JSON y los envía al topic correspondiente. La publicación
 * es <b>best-effort</b>: cualquier fallo de serialización o de envío se registra en el log y
 * <b>no interrumpe</b> la operación de negocio que originó el evento (la transacción ya está
 * persistida en MongoDB).
 * <p>
 * La API pública devuelve tipos de <b>RxJava 3</b> ({@link Completable}) para mantener la
 * reactividad de las nuevas funcionalidades; las capas que trabajan con Reactor los adaptan
 * en el borde mediante {@code Mono.from(completable.toFlowable())}.
 *
 * @author NTT Data
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final KafkaTopicsProperties topics;

    /**
     * Publica el evento de un movimiento ya registrado sobre una cuenta.
     *
     * @param event movimiento a notificar
     * @return {@code Completable} que notifica cuándo terminó el intento de publicación
     */
    public Completable publishAccountTransaction(AccountTransactionEvent event) {
        return send(topics.getAccountTransactions(), event.getAccountId(), event, "account transaction");
    }

    /**
     * Publica el resultado de una operación de monedero para que Yanki lo consuma.
     *
     * @param result resultado de la operación
     * @return {@code Completable} que notifica cuándo terminó el intento de publicación
     */
    public Completable publishWalletOperationResult(WalletOperationResult result) {
        return send(topics.getWalletOperationResults(), result.getOperationId(), result, "wallet operation result");
    }

    /**
     * Serializa el evento y lo envía al topic indicado capturando cualquier error.
     *
     * @param topic       topic destino
     * @param key         clave del mensaje (permite conservar el orden por entidad)
     * @param payload     evento a serializar como JSON
     * @param description descripción usada en el log
     * @return {@code Completable} que siempre completa (nunca propaga error)
     */
    private Completable send(String topic, String key, Object payload, String description) {
        return Completable.fromAction(() -> {
                    String json = objectMapper.writeValueAsString(payload);
                    kafkaTemplate.send(topic, key, json).whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Error publishing {} to topic {}: {}", description, topic, ex.getMessage());
                        } else {
                            log.debug("Published {} to topic {}", description, topic);
                        }
                    });
                })
                .onErrorComplete(ex -> {
                    log.error("Could not serialize/publish {}: {}", description, ex.getMessage());
                    return true;
                });
    }
}
