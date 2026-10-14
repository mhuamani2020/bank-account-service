package com.nttdata.bank_account_service.mapper;

import com.nttdata.bank_account_service.dto.TransactionRequest;
import com.nttdata.bank_account_service.dto.TransactionResponse;
import com.nttdata.bank_account_service.enums.TransactionType;
import com.nttdata.bank_account_service.model.Transaction;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

public class TransactionMapper {

    public static Transaction toEntity(String accountId, TransactionRequest request, Double fee) {
        return Transaction.builder()
                .accountId(accountId)
                .transactionType(TransactionType.valueOf(request.getTransactionType().toUpperCase()))
                .amount(request.getAmount())
                .fee(fee)
                .transactionDate(LocalDateTime.now())
                .build();
    }

    public static TransactionResponse toResponse(Transaction transaction) {
        TransactionResponse response = new TransactionResponse();
        response.setId(transaction.getId());
        response.setAccountId(transaction.getAccountId());
        response.setTransactionType(transaction.getTransactionType().name());
        response.setAmount(transaction.getAmount());
        response.setFee(transaction.getFee());
        // Ajustamos la fecha al formato que espera OpenAPI
        response.setTransactionDate(transaction.getTransactionDate().atOffset(ZoneOffset.UTC));
        return response;
    }
}
