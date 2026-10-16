package com.nttdata.bank_account_service.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Comando/evento entrante que solicita una operación de monedero (Yanki) sobre la cuenta
 * principal asociada a una tarjeta de débito.
 * <p>
 * Lo publica el microservicio de monedero (parte móvil de la Parte III) en el topic
 * {@code wallet-operations}. {@code bank-account-service} lo consume y aplica el cargo o el
 * abono correspondiente sobre la cuenta de la tarjeta, cumpliendo la regla:
 * <i>"Los nuevos microservicios no podrán invocar las APIs de los microservicios usando
 * peticiones REST"</i>. La comunicación entre servicios se realiza exclusivamente por Kafka.
 *
 * @author NTT Data
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WalletOperationEvent {

    /** Identificador único de la operación de monedero (lo usa Yanki para correlacionar). */
    private String operationId;

    /** Tipo de operación solicitada: {@code TOP_UP} (cargar saldo) o {@code WITHDRAW} (abonar). */
    private String operationType;

    /** Tarjeta de débito asociada al monedero cuya cuenta principal se afectará. */
    private String debitCardId;

    /** Importe de la operación. */
    private Double amount;

    /** Fecha y hora de la solicitud en formato ISO-8601. */
    private String requestedAt;
}
