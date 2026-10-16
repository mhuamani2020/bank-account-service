package com.nttdata.bank_account_service.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Evento de dominio que representa un movimiento ya registrado sobre una cuenta bancaria.
 * <p>
 * Forma parte de la <b>arquitectura orientada a eventos</b> de la Parte III: cada vez que se
 * persiste un movimiento (depósito, retiro, transferencia o pago con tarjeta de débito) se
 * publica este evento en el topic de Kafka para que otros microservicios puedan reaccionar
 * sin necesidad de invocar la API REST de este servicio.
 * <p>
 * El evento es un DTO plano (sin lógica de negocio) serializado como JSON; las fechas se
 * transportan en formato ISO-8601 ({@code occurredAt}) para no acoplar los contratos de los
 * consumidores a un tipo de fecha concreto.
 *
 * @author NTT Data
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AccountTransactionEvent {

    /** Identificador único del evento (generado en la publicación). */
    private String eventId;

    /** Cuenta bancaria afectada por el movimiento. */
    private String accountId;

    /** Cliente titular de la cuenta afectada. */
    private String customerId;

    /** Tarjeta de débito usada cuando el movimiento es un pago; {@code null} en el resto. */
    private String debitCardId;

    /** Tipo de movimiento (DEPOSIT, WITHDRAWAL, TRANSFER_IN, TRANSFER_OUT, DEBIT_CARD_PAYMENT). */
    private String transactionType;

    /** Importe del movimiento. */
    private Double amount;

    /** Comisión cobrada por el movimiento. */
    private Double fee;

    /** Saldo de la cuenta después de aplicar el movimiento. */
    private Double balanceAfter;

    /** Identificador del movimiento persistido. */
    private String transactionId;

    /** Fecha y hora de emisión del evento en formato ISO-8601. */
    private String occurredAt;
}
