package com.nttdata.bank_account_service.enums;

/**
 * Tipos de movimiento admitidos sobre una cuenta bancaria.
 *
 * @author NTT Data
 */
public enum TransactionType {
    /** Ingreso de dinero en la cuenta. */
    DEPOSIT,
    /** Retiro de dinero de la cuenta. */
    WITHDRAWAL,
    /** Entrada de una transferencia desde otra cuenta. */
    TRANSFER_IN,
    /** Salida de una transferencia hacia otra cuenta. */
    TRANSFER_OUT
}
