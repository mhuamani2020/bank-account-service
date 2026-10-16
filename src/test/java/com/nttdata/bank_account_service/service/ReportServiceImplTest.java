package com.nttdata.bank_account_service.service;

import com.nttdata.bank_account_service.dto.ProductReportItem;
import com.nttdata.bank_account_service.enums.AccountType;
import com.nttdata.bank_account_service.enums.TransactionType;
import com.nttdata.bank_account_service.model.BankAccount;
import com.nttdata.bank_account_service.model.Transaction;
import com.nttdata.bank_account_service.repository.BankAccountRepository;
import com.nttdata.bank_account_service.repository.TransactionRepository;
import com.nttdata.bank_account_service.service.impl.ReportServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de {@link ReportServiceImpl} (Parte II) con mocks de repositorios.
 *
 * @author NTT Data
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock
    private BankAccountRepository bankAccountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private ReportServiceImpl reportService;

    private BankAccount savingsOne;
    private BankAccount savingsTwo;
    private BankAccount currentOne;

    @BeforeEach
    void setUp() {
        savingsOne = BankAccount.builder().id("acc-1").accountType(AccountType.SAVINGS).balance(100.0).build();
        savingsTwo = BankAccount.builder().id("acc-2").accountType(AccountType.SAVINGS).balance(300.0).build();
        currentOne = BankAccount.builder().id("acc-3").accountType(AccountType.CURRENT).balance(500.0).build();
    }

    @Test
    @DisplayName("getAccountProductsSummary: Agrupa por producto y suma saldos y movimientos")
    void getAccountProductsSummary_shouldAggregateByProduct() {
        Transaction movement1 = Transaction.builder()
                .id("tx-1").accountId("acc-1").transactionType(TransactionType.DEPOSIT)
                .amount(50.0).transactionDate(LocalDateTime.now()).build();
        Transaction movement2 = Transaction.builder()
                .id("tx-2").accountId("acc-2").transactionType(TransactionType.WITHDRAWAL)
                .amount(20.0).transactionDate(LocalDateTime.now()).build();
        Transaction movementCurrent = Transaction.builder()
                .id("tx-3").accountId("acc-3").transactionType(TransactionType.DEPOSIT)
                .amount(70.0).transactionDate(LocalDateTime.now()).build();

        when(bankAccountRepository.findAll()).thenReturn(Flux.just(savingsOne, savingsTwo, currentOne));
        when(transactionRepository.findByTransactionDateBetween(any(), any()))
                .thenReturn(Flux.just(movement1, movement2, movementCurrent));

        StepVerifier.create(reportService.getAccountProductsSummary(LocalDate.now(), LocalDate.now()).toFlowable())
                .assertNext(items -> {
                    // Debe haber una fila por cada tipo de producto del banco
                    assertThat(items).hasSize(AccountType.values().length);
                    ProductReportItem savings = findByType(items, "SAVINGS");
                    assertThat(savings.getNumberOfProducts()).isEqualTo(2L);
                    assertThat(savings.getTotalBalance()).isEqualTo(400.0);
                    assertThat(savings.getNumberOfMovements()).isEqualTo(2L);
                    assertThat(savings.getTotalMovementAmount()).isEqualTo(70.0);

                    ProductReportItem current = findByType(items, "CURRENT");
                    assertThat(current.getNumberOfProducts()).isEqualTo(1L);
                    assertThat(current.getTotalBalance()).isEqualTo(500.0);
                    assertThat(current.getNumberOfMovements()).isEqualTo(1L);
                    assertThat(current.getTotalMovementAmount()).isEqualTo(70.0);

                    // Los tipos sin datos aparecen en cero
                    ProductReportItem fixed = findByType(items, "FIXED_TERM");
                    assertThat(fixed.getNumberOfProducts()).isZero();
                    assertThat(fixed.getTotalBalance()).isEqualTo(0.0);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("getAccountProductsSummary: Error si 'from' es posterior a 'to'")
    void getAccountProductsSummary_whenFromAfterTo_shouldFail() {
        LocalDate from = LocalDate.now();
        LocalDate to = from.minusDays(1);

        StepVerifier.create(reportService.getAccountProductsSummary(from, to).toFlowable())
                .expectErrorMatches(ex -> ex instanceof IllegalArgumentException
                        && ex.getMessage().contains("no puede ser posterior"))
                .verify();
    }

    @Test
    @DisplayName("getAccountProductsSummary: Error si alguna fecha es nula")
    void getAccountProductsSummary_whenDatesNull_shouldFail() {
        StepVerifier.create(reportService.getAccountProductsSummary(null, LocalDate.now()).toFlowable())
                .expectErrorMatches(ex -> ex instanceof IllegalArgumentException
                        && ex.getMessage().contains("obligatorias"))
                .verify();
    }

    @Test
    @DisplayName("getDebitCardLastMovements: Devuelve los movimientos de la tarjeta")
    void getDebitCardLastMovements_shouldReturnTransactions() {
        Transaction movement = Transaction.builder()
                .id("tx-card").accountId("acc-1").transactionType(TransactionType.DEBIT_CARD_PAYMENT)
                .amount(80.0).debitCardId("card-9").transactionDate(LocalDateTime.now()).build();

        when(transactionRepository.findTop10ByDebitCardIdOrderByTransactionDateDesc("card-9"))
                .thenReturn(Flux.just(movement));

        StepVerifier.create(reportService.getDebitCardLastMovements("card-9"))
                .assertNext(response -> {
                    assertThat(response.getId()).isEqualTo("tx-card");
                    assertThat(response.getDebitCardId()).isEqualTo("card-9");
                })
                .verifyComplete();
    }

    private ProductReportItem findByType(List<ProductReportItem> items, String productType) {
        return items.stream()
                .filter(item -> productType.equals(item.getProductType()))
                .findFirst()
                .orElseThrow();
    }
}
