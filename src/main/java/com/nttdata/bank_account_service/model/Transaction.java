package com.nttdata.bank_account_service.model;

import com.nttdata.bank_account_service.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Entidad de negocio que representa un movimiento (transacción) de una cuenta bancaria,
 * persistida en MongoDB.
 * <p>
 * Colección propia ({@code transactions}), en cumplimiento del patrón
 * <i>database per service</i>. Registra el importe, la comisión cobrada y la fecha y
 * hora en que se realizó el movimiento; la fecha es la que permite contar cuántos
 * movimientos lleva la cuenta en el mes en curso.
 *
 * @author NTT Data
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "transactions")
public class Transaction {
    /** Identificador autogenerado del movimiento. */
    @Id
    private String id;

    /** Cuenta bancaria sobre la que se registró el movimiento. */
    private String accountId;

    /** Tipo de movimiento: DEPOSIT, WITHDRAWAL, TRANSFER_IN o TRANSFER_OUT. */
    private TransactionType transactionType;

    /** Importe del movimiento. */
    private Double amount;

    /** Comisión cobrada por este movimiento (límite mensual y/o mantenimiento). */
    private Double fee;

    /**
     * Identificador de la tarjeta de débito cuando el movimiento es un pago realizado
     * con ella (tipo {@code DEBIT_CARD_PAYMENT}); {@code null} en el resto de casos.
     * Permite consultar los movimientos de una tarjeta de débito concreta.
     */
    private String debitCardId;

    /** Fecha y hora en que se registró el movimiento. */
    private LocalDateTime transactionDate;
}
