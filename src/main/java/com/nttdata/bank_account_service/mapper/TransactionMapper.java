package com.nttdata.bank_account_service.mapper;

import com.nttdata.bank_account_service.dto.TransactionRequest;
import com.nttdata.bank_account_service.dto.TransactionResponse;
import com.nttdata.bank_account_service.enums.TransactionType;
import com.nttdata.bank_account_service.model.Transaction;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Conversor estático entre el DTO de la API ({@link TransactionRequest} /
 * {@link TransactionResponse}) y la entidad persistida {@link Transaction}.
 * <p>
 * Se mantiene como clase de métodos estáticos porque la transformación no tiene estado.
 *
 * @author NTT Data
 */
public class TransactionMapper {

    /** Clase de utilidad: no se instancia. */
    private TransactionMapper() {
    }

    /**
     * Convierte la petición REST en la entidad que se persistirá en MongoDB, fijando la
     * fecha y hora actuales del movimiento.
     *
     * @param accountId cuenta sobre la que se registra el movimiento
     * @param request   tipo e importe del movimiento
     * @param fee       comisión cobrada en este movimiento
     * @return entidad {@link Transaction} lista para guardarse
     */
    public static Transaction toEntity(String accountId, TransactionRequest request, Double fee) {
        return Transaction.builder()
                .accountId(accountId)
                .transactionType(TransactionType.valueOf(request.getTransactionType().toUpperCase()))
                .amount(request.getAmount())
                .fee(fee)
                .debitCardId(request.getDebitCardId())
                .transactionDate(LocalDateTime.now())
                .build();
    }

    /**
     * Convierte la entidad persistida en la respuesta REST que consume el cliente,
     * ajustando la fecha al formato OffsetDateTime que espera OpenAPI.
     *
     * @param transaction movimiento leído de MongoDB
     * @return DTO de respuesta con los campos públicos del movimiento
     */
    public static TransactionResponse toResponse(Transaction transaction) {
        TransactionResponse response = new TransactionResponse();
        response.setId(transaction.getId());
        response.setAccountId(transaction.getAccountId());
        response.setTransactionType(transaction.getTransactionType().name());
        response.setAmount(transaction.getAmount());
        response.setFee(transaction.getFee());
        response.setDebitCardId(transaction.getDebitCardId());
        // Ajustamos la fecha al formato que espera OpenAPI
        response.setTransactionDate(transaction.getTransactionDate().atOffset(ZoneOffset.UTC));
        return response;
    }
}
