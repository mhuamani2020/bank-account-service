package com.nttdata.bank_account_service.service.impl;

import com.nttdata.bank_account_service.dto.DebitCardPaymentRequest;
import com.nttdata.bank_account_service.dto.DebitCardPaymentResponse;
import com.nttdata.bank_account_service.dto.DebitCardRequest;
import com.nttdata.bank_account_service.dto.DebitCardResponse;
import com.nttdata.bank_account_service.dto.TransactionRequest;
import com.nttdata.bank_account_service.dto.TransactionResponse;
import com.nttdata.bank_account_service.enums.AccountType;
import com.nttdata.bank_account_service.enums.DebitCardStatus;
import com.nttdata.bank_account_service.enums.TransactionType;
import com.nttdata.bank_account_service.mapper.DebitCardMapper;
import com.nttdata.bank_account_service.mapper.TransactionMapper;
import com.nttdata.bank_account_service.model.BankAccount;
import com.nttdata.bank_account_service.model.DebitCard;
import com.nttdata.bank_account_service.repository.BankAccountRepository;
import com.nttdata.bank_account_service.repository.DebitCardRepository;
import com.nttdata.bank_account_service.repository.TransactionRepository;
import com.nttdata.bank_account_service.service.DebitCardService;
import com.nttdata.bank_account_service.service.TransactionService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Implementación del servicio de tarjetas de débito (Parte III) con el modelo de
 * reactividad de <b>RxJava 3</b> ({@link Single}, {@link Maybe}, {@link Flowable} y
 * {@link Completable}).
 * <p>
 * Aplica las reglas de negocio:
 * <ul>
 *     <li>la tarjeta se asocia a una cuenta bancaria existente;</li>
 *     <li>una cuenta tiene como máximo una tarjeta de débito;</li>
 *     <li>no se emiten tarjetas para cuentas a plazo fijo (solo admiten depósito/retiro);</li>
 *     <li>los pagos debitan la cuenta asociada y solo se permiten si la tarjeta está
 *     {@code ACTIVE} y hay saldo suficiente.</li>
 * </ul>
 * Los repositorios reactivos (Reactor) se adaptan al borde con {@code fromPublisher}; el
 * resto de la composición usa operadores de RxJava. Los pagos se delegan en
 * {@link TransactionService} para reutilizar el cálculo de comisiones y el control del
 * límite mensual de movimientos del producto.
 *
 * @author NTT Data
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DebitCardServiceImpl implements DebitCardService {

    private final DebitCardRepository debitCardRepository;
    private final BankAccountRepository bankAccountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;

    @Override
    public Single<DebitCardResponse> create(DebitCardRequest request) {
        if (request == null || request.getAccountId() == null || request.getAccountId().isBlank()) {
            return Single.error(new IllegalArgumentException("El identificador de la cuenta es obligatorio"));
        }

        return Maybe.fromPublisher(bankAccountRepository.findById(request.getAccountId()))
                .switchIfEmpty(Maybe.error(new IllegalArgumentException("La cuenta bancaria no existe")))
                .flatMap(account -> {
                    if (account.getAccountType() == AccountType.FIXED_TERM) {
                        return Maybe.error(new IllegalArgumentException(
                                "No se pueden emitir tarjetas de débito para cuentas a plazo fijo"));
                    }
                    return Maybe.fromPublisher(debitCardRepository.existsByAccountId(account.getId()))
                            .flatMap(exists -> {
                                if (Boolean.TRUE.equals(exists)) {
                                    return Maybe.error(new IllegalArgumentException(
                                            "La cuenta ya tiene una tarjeta de débito asociada"));
                                }
                                DebitCardStatus status = resolveStatus(request.getStatus());
                                DebitCard card = DebitCardMapper.toEntity(account, status);
                                log.info("Emitting debit card for account {}", account.getId());
                                return Maybe.fromPublisher(debitCardRepository.save(card));
                            });
                })
                .map(DebitCardMapper::toResponse)
                .toSingle();
    }

    @Override
    public Flowable<DebitCardResponse> findAll() {
        return Flowable.fromPublisher(debitCardRepository.findAll()).map(DebitCardMapper::toResponse);
    }

    @Override
    public Maybe<DebitCardResponse> findById(String id) {
        return Maybe.fromPublisher(debitCardRepository.findById(id)).map(DebitCardMapper::toResponse);
    }

    @Override
    public Maybe<DebitCardResponse> update(String id, DebitCardRequest request) {
        return Maybe.fromPublisher(debitCardRepository.findById(id))
                .flatMap(card -> {
                    if (request != null && request.getStatus() != null && !request.getStatus().isBlank()) {
                        card.setStatus(resolveStatus(request.getStatus()));
                    }
                    return Maybe.fromPublisher(debitCardRepository.save(card));
                })
                .map(DebitCardMapper::toResponse);
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(
                debitCardRepository.findById(id).flatMap(card -> debitCardRepository.delete(card)));
    }

    @Override
    public Flowable<DebitCardResponse> findByCustomer(String customerId) {
        return Flowable.fromPublisher(debitCardRepository.findByCustomerId(customerId))
                .map(DebitCardMapper::toResponse);
    }

    @Override
    public Single<DebitCardPaymentResponse> pay(String cardId, DebitCardPaymentRequest request) {
        if (request == null || request.getAmount() == null || request.getAmount() <= 0) {
            return Single.error(new IllegalArgumentException("El importe del pago debe ser mayor que cero"));
        }

        return Maybe.fromPublisher(debitCardRepository.findById(cardId))
                .switchIfEmpty(Maybe.error(new IllegalArgumentException("La tarjeta de débito no existe")))
                .flatMap(card -> {
                    if (card.getStatus() != DebitCardStatus.ACTIVE) {
                        return Maybe.error(new IllegalArgumentException(
                                "La tarjeta de débito no está activa para realizar pagos"));
                    }
                    TransactionRequest transactionRequest = new TransactionRequest();
                    transactionRequest.setTransactionType(TransactionType.DEBIT_CARD_PAYMENT.name());
                    transactionRequest.setAmount(request.getAmount());
                    transactionRequest.setDebitCardId(card.getId());

                    return Maybe.fromPublisher(
                                    transactionService.createTransaction(card.getAccountId(), transactionRequest))
                            .flatMapSingle(transaction -> buildPaymentResponse(card, transaction));
                })
                .toSingle();
    }

    @Override
    public Flowable<TransactionResponse> getMovements(String cardId) {
        return Flowable.fromPublisher(transactionRepository.findByDebitCardIdOrderByTransactionDateDesc(cardId))
                .map(TransactionMapper::toResponse);
    }

    /**
     * Compone la respuesta del pago leyendo el saldo resultante de la cuenta asociada.
     *
     * @param card        tarjeta con la que se pagó
     * @param transaction movimiento generado por el pago
     * @return {@code Single} con el resultado del pago
     */
    private Single<DebitCardPaymentResponse> buildPaymentResponse(DebitCard card, TransactionResponse transaction) {
        return Single.fromPublisher(bankAccountRepository.findById(card.getAccountId()))
                .map(account -> toPaymentResponse(card, transaction, account));
    }

    /**
     * Mapea el pago a su DTO de respuesta.
     *
     * @param card        tarjeta usada
     * @param transaction movimiento del pago
     * @param account     cuenta ya debitada
     * @return DTO de respuesta del pago
     */
    private DebitCardPaymentResponse toPaymentResponse(DebitCard card, TransactionResponse transaction,
                                                       BankAccount account) {
        DebitCardPaymentResponse response = new DebitCardPaymentResponse();
        response.setCardId(card.getId());
        response.setAccountId(card.getAccountId());
        response.setTransactionId(transaction.getId());
        response.setAmount(transaction.getAmount());
        response.setFee(transaction.getFee());
        response.setBalanceAfter(account.getBalance());
        response.setPaymentDate(transaction.getTransactionDate());
        return response;
    }

    /**
     * Normaliza el estado recibido en texto (por defecto {@code ACTIVE}) y valida que
     * sea un estado admitido.
     *
     * @param status estado recibido, puede ser nulo o vacío
     * @return estado válido de tarjeta
     */
    private DebitCardStatus resolveStatus(String status) {
        if (status == null || status.isBlank()) {
            return DebitCardStatus.ACTIVE;
        }
        try {
            return DebitCardStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Estado de tarjeta inválido: " + status);
        }
    }
}
