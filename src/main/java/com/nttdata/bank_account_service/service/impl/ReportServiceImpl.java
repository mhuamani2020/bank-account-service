package com.nttdata.bank_account_service.service.impl;

import com.nttdata.bank_account_service.dto.ProductReportItem;
import com.nttdata.bank_account_service.dto.TransactionResponse;
import com.nttdata.bank_account_service.enums.AccountType;
import com.nttdata.bank_account_service.mapper.TransactionMapper;
import com.nttdata.bank_account_service.model.BankAccount;
import com.nttdata.bank_account_service.model.Transaction;
import com.nttdata.bank_account_service.repository.BankAccountRepository;
import com.nttdata.bank_account_service.repository.TransactionRepository;
import com.nttdata.bank_account_service.service.ReportService;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implementación del servicio de reportes de productos de cuenta (Parte II) con el modelo
 * de reactividad de <b>RxJava 3</b> ({@link Single} / {@link Flowable}).
 * <p>
 * La agregación se realiza en memoria con las <b>APIs de Streams</b> de Java (requisito de
 * la Parte II), agrupando las cuentas por tipo de producto y cruzando sus movimientos del
 * intervalo solicitado. El reporte cubre todos los tipos de producto del banco, de modo
 * que los tipos sin datos aparecen con valores en cero. Los repositorios reactivos
 * (Reactor) se adaptan al borde con {@code fromPublisher}.
 *
 * @author NTT Data
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final BankAccountRepository bankAccountRepository;
    private final TransactionRepository transactionRepository;

    @Override
    public Single<List<ProductReportItem>> getAccountProductsSummary(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return Single.error(new IllegalArgumentException("Las fechas 'from' y 'to' son obligatorias"));
        }
        if (from.isAfter(to)) {
            return Single.error(new IllegalArgumentException("La fecha 'from' no puede ser posterior a 'to'"));
        }

        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay(); // intervalo [from, to] inclusive

        log.info("Generating account products report from {} to {}", from, to);

        Single<List<BankAccount>> accounts =
                Single.fromPublisher(bankAccountRepository.findAll().collectList());
        Single<List<Transaction>> transactions =
                Single.fromPublisher(transactionRepository.findByTransactionDateBetween(start, end).collectList());

        return Single.zip(accounts, transactions, this::buildReport);
    }

    @Override
    public Flowable<TransactionResponse> getDebitCardLastMovements(String cardId) {
        return Flowable.fromPublisher(transactionRepository.findTop10ByDebitCardIdOrderByTransactionDateDesc(cardId))
                .map(TransactionMapper::toResponse);
    }

    /**
     * Construye el reporte a partir de las cuentas y los movimientos, usando Streams.
     *
     * @param accounts     todas las cuentas del banco
     * @param transactions movimientos registrados en el intervalo
     * @return una fila de reporte por cada tipo de cuenta
     */
    private List<ProductReportItem> buildReport(List<BankAccount> accounts, List<Transaction> transactions) {
        Map<String, BankAccount> accountById = accounts.stream()
                .collect(Collectors.toMap(BankAccount::getId, Function.identity(), (a, b) -> a));

        Map<String, List<BankAccount>> accountsByType = accounts.stream()
                .filter(account -> account.getAccountType() != null)
                .collect(Collectors.groupingBy(account -> account.getAccountType().name()));

        Map<String, List<Transaction>> movementsByType = transactions.stream()
                .filter(transaction -> accountById.containsKey(transaction.getAccountId()))
                .collect(Collectors.groupingBy(transaction ->
                        accountById.get(transaction.getAccountId()).getAccountType().name()));

        return Arrays.stream(AccountType.values())
                .map(type -> toReportItem(type.name(),
                        accountsByType.getOrDefault(type.name(), List.of()),
                        movementsByType.getOrDefault(type.name(), List.of())))
                .collect(Collectors.toList());
    }

    /**
     * Compone una fila del reporte para un tipo de producto.
     *
     * @param productType tipo de producto
     * @param accounts    cuentas de ese tipo
     * @param movements   movimientos de ese tipo en el intervalo
     * @return fila del reporte
     */
    private ProductReportItem toReportItem(String productType, List<BankAccount> accounts,
                                           List<Transaction> movements) {
        double totalBalance = accounts.stream()
                .mapToDouble(account -> account.getBalance() != null ? account.getBalance() : 0.0)
                .sum();
        double totalMovementAmount = movements.stream()
                .mapToDouble(transaction -> transaction.getAmount() != null ? transaction.getAmount() : 0.0)
                .sum();

        ProductReportItem item = new ProductReportItem();
        item.setProductType(productType);
        item.setNumberOfProducts((long) accounts.size());
        item.setTotalBalance(totalBalance);
        item.setNumberOfMovements((long) movements.size());
        item.setTotalMovementAmount(totalMovementAmount);
        return item;
    }
}
