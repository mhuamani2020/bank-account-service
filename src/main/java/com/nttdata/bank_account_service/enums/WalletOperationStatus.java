package com.nttdata.bank_account_service.enums;

/**
 * Estados posibles del resultado de una operación de monedero procesada por
 * {@code bank-account-service}.
 *
 * @author NTT Data
 */
public enum WalletOperationStatus {

    /** La operación se aplicó correctamente sobre la cuenta asociada. */
    COMPLETED,

    /** La operación no pudo aplicarse (datos inválidos, tarjeta inexistente o saldo insuficiente). */
    FAILED
}
