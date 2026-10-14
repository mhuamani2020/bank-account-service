package com.nttdata.bank_account_service.service.impl;

import com.nttdata.bank_account_service.dto.TransactionRequest;
import com.nttdata.bank_account_service.dto.TransactionResponse;
import com.nttdata.bank_account_service.dto.TransferRequest;
import com.nttdata.bank_account_service.enums.TransactionType;
import com.nttdata.bank_account_service.mapper.TransactionMapper;
import com.nttdata.bank_account_service.repository.BankAccountRepository;
import com.nttdata.bank_account_service.repository.TransactionRepository;
import com.nttdata.bank_account_service.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final BankAccountRepository accountRepository;

    // Comisión por defecto si se supera el límite de movimientos
    private static final Double DEFAULT_FEE = 3.50;

    @Override
    public Mono<TransactionResponse> createTransaction(String accountId, TransactionRequest request) {
        TransactionType type = TransactionType.valueOf(request.getTransactionType().toUpperCase());
        Double amount = request.getAmount();

        // 1. Buscamos la cuenta
        return accountRepository.findById(accountId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("La cuenta no existe")))
                .flatMap(account -> {
                    // 2. Contamos cuántos movimientos lleva en el mes
                    return transactionRepository.countByAccountId(accountId)
                            .flatMap(count -> {
                                Double fee = 0.0;

                                // Regla: Si supera el límite de movimientos, se cobra comisión
                                if (account.getMaxMovements() != null && count >= account.getMaxMovements()) {
                                    fee = DEFAULT_FEE;
                                    log.info("Límite de movimientos superado para cuenta {}. Se cobrará comisión de {}", accountId, fee);
                                }

                                // 3. Validamos saldos y actualizamos
                                if (type == TransactionType.WITHDRAWAL || type == TransactionType.TRANSFER_OUT) {
                                    if (account.getBalance() < (amount + fee)) {
                                        return Mono.error(new IllegalArgumentException("Saldo insuficiente para el retiro/transferencia y comisiones"));
                                    }
                                    account.setBalance(account.getBalance() - amount - fee);
                                } else if (type == TransactionType.DEPOSIT || type == TransactionType.TRANSFER_IN) {
                                    account.setBalance(account.getBalance() + amount - fee);
                                }

                                // 4. Guardamos la cuenta con el nuevo saldo, Y LUEGO guardamos el historial de la transacción
                                Double finalFee = fee;
                                return accountRepository.save(account)
                                        .then(transactionRepository.save(TransactionMapper.toEntity(accountId, request, finalFee)))
                                        .map(TransactionMapper::toResponse);
                            });
                });
    }

    @Override
    public Flux<TransactionResponse> getTransactionsByAccount(String accountId) {
        return transactionRepository.findByAccountId(accountId)
                .map(TransactionMapper::toResponse);
    }

    @Override
    public Mono<Void> createTransfer(TransferRequest request) {
        log.info("Iniciando transferencia de cuenta {} a cuenta {} por {}",
                request.getSourceAccountId(), request.getTargetAccountId(), request.getAmount());

        // Preparamos la petición de retiro para la cuenta origen
        TransactionRequest withdrawal = new TransactionRequest();
        withdrawal.setTransactionType(TransactionType.TRANSFER_OUT.name());
        withdrawal.setAmount(request.getAmount());

        // Preparamos la petición de depósito para la cuenta destino
        TransactionRequest deposit = new TransactionRequest();
        deposit.setTransactionType(TransactionType.TRANSFER_IN.name());
        deposit.setAmount(request.getAmount());

        // Ejecutamos en cadena: Primero retiramos, luego depositamos
        return createTransaction(request.getSourceAccountId(), withdrawal)
                .then(createTransaction(request.getTargetAccountId(), deposit))
                .then(); // Retornamos Mono<Void> al terminar
    }
}