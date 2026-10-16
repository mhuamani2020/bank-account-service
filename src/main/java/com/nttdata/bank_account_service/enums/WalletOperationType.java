package com.nttdata.bank_account_service.enums;

/**
 * Tipos de operación admitidos sobre un monedero móvil (Yanki) que afectan a la cuenta
 * principal asociada a una tarjeta de débito.
 *
 * @author NTT Data
 */
public enum WalletOperationType {

    /**
     * Cargar saldo en el monedero desde la cuenta: se <b>debita</b> la cuenta principal
     * asociada a la tarjeta de débito.
     */
    TOP_UP,

    /**
     * Abonar (retirar) saldo del monedero hacia la cuenta: se <b>acredita</b> la cuenta
     * principal asociada a la tarjeta de débito.
     */
    WITHDRAW
}
