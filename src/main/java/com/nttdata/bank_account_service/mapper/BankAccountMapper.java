package com.nttdata.bank_account_service.mapper;

import com.nttdata.bank_account_service.dto.BankAccountRequest;
import com.nttdata.bank_account_service.dto.BankAccountResponse;
import com.nttdata.bank_account_service.enums.AccountType;
import com.nttdata.bank_account_service.model.BankAccount;

/**
 * Conversor estático entre el DTO de entrada/salida de la API ({@link BankAccountRequest}
 * / {@link BankAccountResponse}) y la entidad persistida {@link BankAccount}.
 * <p>
 * Se mantiene como clase de métodos estáticos para evitar una dependencia innecesaria en
 * el contenedor de Spring y para dejar explícito que la transformación no tiene estado.
 *
 * @author NTT Data
 */
public class BankAccountMapper {

    /** Clase de utilidad: no se instancia. */
    private BankAccountMapper() {
    }

    /**
     * Convierte la petición REST en la entidad que se persistirá en MongoDB.
     * <p>
     * Solo transforma la forma: las reglas de producto (comisión, límite de movimientos,
     * titulares) las aplica el servicio de negocio antes de guardar.
     *
     * @param request petición con los datos enviados por el cliente
     * @return entidad {@link BankAccount} lista para ser guardada
     */
    public static BankAccount toEntity(BankAccountRequest request) {
        return BankAccount.builder()
                .customerId(request.getCustomerId())
                .accountType(AccountType.valueOf(request.getAccountType().toUpperCase()))
                .balance(request.getBalance() != null ? request.getBalance() : 0.0)
                .maintenanceFee(request.getMaintenanceFee())
                .maxMovements(request.getMaxMovements())
                .fixedTermWithdrawalDay(request.getFixedTermWithdrawalDay())
                .holders(request.getHolders())
                .signatories(request.getSignatories())
                .build();
    }

    /**
     * Convierte la entidad persistida en la respuesta REST que consume el cliente.
     *
     * @param account entidad leída de MongoDB
     * @return DTO de respuesta con los campos públicos de la cuenta
     */
    public static BankAccountResponse toResponse(BankAccount account) {
        BankAccountResponse response = new BankAccountResponse();
        response.setId(account.getId());
        response.setCustomerId(account.getCustomerId());
        response.setAccountType(account.getAccountType() != null ? account.getAccountType().name() : null);
        response.setBalance(account.getBalance());
        response.setMaintenanceFee(account.getMaintenanceFee());
        response.setMaxMovements(account.getMaxMovements());
        response.setFixedTermWithdrawalDay(account.getFixedTermWithdrawalDay());
        response.setHolders(account.getHolders());
        response.setSignatories(account.getSignatories());
        return response;
    }
}
