package com.nttdata.bank_account_service.enums;

/**
 * Estado de una tarjeta de débito (Parte III).
 * <p>
 * Una tarjeta {@link #ACTIVE} puede realizar pagos; una {@link #BLOCKED} tiene todos
 * los pagos rechazados hasta que se vuelva a activar.
 *
 * @author NTT Data
 */
public enum DebitCardStatus {
    /** Tarjeta operativa: permite pagos. */
    ACTIVE,
    /** Tarjeta bloqueada: no permite pagos. */
    BLOCKED
}
