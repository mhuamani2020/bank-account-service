package com.nttdata.bank_account_service.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Evento de resultado de una operación de monedero procesada sobre la cuenta de una tarjeta
 * de débito.
 * <p>
 * {@code bank-account-service} lo publica en el topic {@code wallet-operation-results} una vez
 * aplicado el cargo/abono (o detectado el error), de modo que el microservicio de monedero
 * (Yanki) conozca el desenlace de su solicitud sin haber invocado ninguna API REST.
 *
 * @author NTT Data
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WalletOperationResult {

    /** Identificador de la operación de monedero a la que responde este resultado. */
    private String operationId;

    /** Estado del procesamiento: {@code COMPLETED} o {@code FAILED}. */
    private String status;

    /** Identificador del movimiento generado en la cuenta (solo si {@code COMPLETED}). */
    private String transactionId;

    /** Saldo de la cuenta después de la operación (solo si {@code COMPLETED}). */
    private Double balanceAfter;

    /** Descripción del error (solo si {@code FAILED}). */
    private String errorMessage;

    /** Fecha y hora del procesamiento en formato ISO-8601. */
    private String processedAt;
}
