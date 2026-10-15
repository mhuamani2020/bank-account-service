package com.nttdata.bank_account_service.enums;

/**
 * Tipos de producto bancario (Pasivo) que puede ofrecer el banco.
 * <p>
 * Cada tipo aplica unas reglas de producto distintas sobre comisión de mantenimiento y
 * límite de movimientos mensuales.
 *
 * @author NTT Data
 */
public enum AccountType {
    /** Ahorro: libre de comisión de mantenimiento y con límite máximo de movimientos mensuales. */
    SAVINGS,

    /** Cuenta corriente: con comisión de mantenimiento y sin límite de movimientos mensuales. */
    CURRENT,

    /** Plazo fijo: libre de comisión y con un único movimiento (retiro o depósito) en un día concreto del mes. */
    FIXED_TERM,

    /** Cuenta de ahorro VIP (Fase II). */
    VIP,

    /** Cuenta corriente PYME (Fase II). */
    PYME
}
