package com.nttdata.bank_account_service.mapper;

import com.nttdata.bank_account_service.dto.BankAccountRequest;
import com.nttdata.bank_account_service.dto.BankAccountResponse;
import com.nttdata.bank_account_service.enums.AccountType;
import com.nttdata.bank_account_service.model.BankAccount;

public class BankAccountMapper {
    public static BankAccount toEntity(BankAccountRequest request) {
        return BankAccount.builder()
                .customerId(request.getCustomerId())
                .accountType(AccountType.valueOf(request.getAccountType().toUpperCase()))
                .balance(request.getBalance() != null ? request.getBalance() : 0.0)
                .maintenanceFee(request.getMaintenanceFee())
                .maxMovements(request.getMaxMovements())
                .build();
    }

    public static BankAccountResponse toResponse(BankAccount account) {
        BankAccountResponse response = new BankAccountResponse();
        response.setId(account.getId());
        response.setCustomerId(account.getCustomerId());
        response.setAccountType(account.getAccountType() != null ? account.getAccountType().name() : null);
        response.setBalance(account.getBalance());
        response.setMaintenanceFee(account.getMaintenanceFee());
        response.setMaxMovements(account.getMaxMovements());
        return response;
    }
}
