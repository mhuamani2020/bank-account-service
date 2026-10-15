package com.nttdata.bank_account_service.enums;

/**
 * Tipo de cliente de un banco. Determina qué productos bancarios puede contratar.
 *
 * @author NTT Data
 */
public enum CustomerType {
    /** Persona natural: máximo una cuenta de cada tipo. */
    PERSONAL,
    /** Persona jurídica: solo puede tener cuentas corrientes, con uno o más titulares. */
    BUSINESS
}
