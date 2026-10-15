package com.nttdata.bank_account_service.service;

import com.nttdata.bank_account_service.dto.TransactionRequest;
import com.nttdata.bank_account_service.dto.TransactionResponse;
import com.nttdata.bank_account_service.dto.TransferRequest;
import com.nttdata.bank_account_service.enums.AccountType;
import com.nttdata.bank_account_service.enums.TransactionType;
import com.nttdata.bank_account_service.model.BankAccount;
import com.nttdata.bank_account_service.model.Transaction;
import com.nttdata.bank_account_service.repository.BankAccountRepository;
import com.nttdata.bank_account_service.repository.TransactionRepository;
import com.nttdata.bank_account_service.service.impl.TransactionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private BankAccountRepository accountRepository;

    @InjectMocks
    private TransactionServiceImpl transactionService;

    private BankAccount sourceAccount;
    private BankAccount targetAccount;
    private BankAccount fixedTermAccount;
    private Transaction transactionEntity;

    @BeforeEach
    void setUp() {
        sourceAccount = BankAccount.builder()
                .id("acc-001")
                .customerId("cust-01")
                .accountType(AccountType.SAVINGS)
                .balance(1000.0)
                .maintenanceFee(0.0)
                .maxMovements(5)
                .build();

        targetAccount = BankAccount.builder()
                .id("acc-002")
                .customerId("cust-02")
                .accountType(AccountType.CURRENT)
                .balance(200.0)
                .maintenanceFee(0.0)
                .maxMovements(10)
                .build();

        fixedTermAccount = BankAccount.builder()
                .id("acc-003")
                .customerId("cust-03")
                .accountType(AccountType.FIXED_TERM)
                .balance(5000.0)
                .maintenanceFee(0.0)
                .maxMovements(1)
                .fixedTermWithdrawalDay(java.time.LocalDate.now().getDayOfMonth())
                .build();

        transactionEntity = Transaction.builder()
                .id("tx-001")
                .accountId("acc-001")
                .transactionType(TransactionType.DEPOSIT)
                .amount(100.0)
                .fee(0.0)
                .transactionDate(LocalDateTime.now())
                .build();
    }

    // --- CREATE TRANSACTION: DEPOSIT ---

    @Test
    @DisplayName("createTransaction: Error si la cuenta no existe")
    void createTransaction_accountNotFound_shouldThrowException() {
        TransactionRequest request = new TransactionRequest();
        request.setTransactionType("DEPOSIT");
        request.setAmount(100.0);

        when(accountRepository.findById("acc-999")).thenReturn(Mono.empty());

        Mono<TransactionResponse> result = transactionService.createTransaction("acc-999", request);

        StepVerifier.create(result)
                .expectErrorMatches(ex -> ex instanceof IllegalArgumentException
                        && "La cuenta no existe".equals(ex.getMessage()))
                .verify();

        verify(accountRepository, times(1)).findById("acc-999");
        verifyNoInteractions(transactionRepository);
    }

    @Test
    @DisplayName("createTransaction: Error si el monto no es mayor que cero")
    void createTransaction_amountNotPositive_shouldThrowException() {
        TransactionRequest request = new TransactionRequest();
        request.setTransactionType("DEPOSIT");
        request.setAmount(0.0);

        Mono<TransactionResponse> result = transactionService.createTransaction("acc-001", request);

        StepVerifier.create(result)
                .expectErrorMatches(ex -> ex instanceof IllegalArgumentException
                        && ex.getMessage().contains("monto del movimiento debe ser mayor que cero"))
                .verify();

        verifyNoInteractions(accountRepository);
        verifyNoInteractions(transactionRepository);
    }

    @Test
    @DisplayName("createTransaction: Deposito exitoso sin comision cuando no supera limite")
    void createTransaction_depositWithoutFee_shouldIncreaseBalance() {
        TransactionRequest request = new TransactionRequest();
        request.setTransactionType("DEPOSIT");
        request.setAmount(200.0);

        when(accountRepository.findById("acc-001")).thenReturn(Mono.just(sourceAccount));
        // El recuento de movimientos es MENSUAL (solo los del mes en curso)
        when(transactionRepository.countByAccountIdAndTransactionDateBetween(eq("acc-001"), any(), any()))
                .thenReturn(Mono.just(2L)); // Límite es 5
        when(accountRepository.save(any(BankAccount.class))).thenReturn(Mono.just(sourceAccount));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(Mono.just(transactionEntity));

        Mono<TransactionResponse> result = transactionService.createTransaction("acc-001", request);

        StepVerifier.create(result)
                .expectNextMatches(res -> "tx-001".equals(res.getId()))
                .verifyComplete();

        // Saldo previo 1000 + 200 = 1200
        verify(accountRepository).save(argThat(acc -> Double.valueOf(1200.0).equals(acc.getBalance())));
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    @DisplayName("createTransaction: Deposito con comision cuando supera limite de movimientos")
    void createTransaction_depositWithFee_whenExceedsMovements() {
        TransactionRequest request = new TransactionRequest();
        request.setTransactionType("DEPOSIT");
        request.setAmount(100.0);

        when(accountRepository.findById("acc-001")).thenReturn(Mono.just(sourceAccount));
        when(transactionRepository.countByAccountIdAndTransactionDateBetween(eq("acc-001"), any(), any()))
                .thenReturn(Mono.just(5L)); // Igual al límite (5)
        when(accountRepository.save(any(BankAccount.class))).thenReturn(Mono.just(sourceAccount));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(Mono.just(transactionEntity));

        Mono<TransactionResponse> result = transactionService.createTransaction("acc-001", request);

        StepVerifier.create(result)
                .expectNextCount(1)
                .verifyComplete();

        // Saldo previo 1000 + 100 - 3.50 fee = 1096.50
        verify(accountRepository).save(argThat(acc -> Double.valueOf(1096.50).equals(acc.getBalance())));
    }

    @Test
    @DisplayName("createTransaction: Cuenta corriente cobra comision de mantenimiento en el primer movimiento del mes")
    void createTransaction_currentAccount_chargesMaintenanceFeeOnFirstMovementOfMonth() {
        BankAccount currentAccount = BankAccount.builder()
                .id("acc-010")
                .customerId("cust-02")
                .accountType(AccountType.CURRENT)
                .balance(1000.0)
                .maintenanceFee(15.0)
                .maxMovements(null) // Sin límite de movimientos mensuales
                .build();

        TransactionRequest request = new TransactionRequest();
        request.setTransactionType("DEPOSIT");
        request.setAmount(200.0);

        when(accountRepository.findById("acc-010")).thenReturn(Mono.just(currentAccount));
        when(transactionRepository.countByAccountIdAndTransactionDateBetween(eq("acc-010"), any(), any()))
                .thenReturn(Mono.just(0L)); // Primer movimiento del mes
        when(accountRepository.save(any(BankAccount.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        Mono<TransactionResponse> result = transactionService.createTransaction("acc-010", request);

        StepVerifier.create(result)
                .expectNextMatches(res -> Double.valueOf(15.0).equals(res.getFee()))
                .verifyComplete();

        // Sin límite de movimientos no se añade comisión por exceso: 1000 + 200 - 15 = 1185
        verify(accountRepository).save(argThat(acc -> Double.valueOf(1185.0).equals(acc.getBalance())));
    }

    @Test
    @DisplayName("createTransaction: Cuenta corriente no cobra comision de mantenimiento fuera del primer movimiento")
    void createTransaction_currentAccount_doesNotChargeMaintenanceFeeAfterFirstMovement() {
        BankAccount currentAccount = BankAccount.builder()
                .id("acc-010")
                .customerId("cust-02")
                .accountType(AccountType.CURRENT)
                .balance(1000.0)
                .maintenanceFee(15.0)
                .build();

        TransactionRequest request = new TransactionRequest();
        request.setTransactionType("DEPOSIT");
        request.setAmount(200.0);

        when(accountRepository.findById("acc-010")).thenReturn(Mono.just(currentAccount));
        when(transactionRepository.countByAccountIdAndTransactionDateBetween(eq("acc-010"), any(), any()))
                .thenReturn(Mono.just(3L));
        when(accountRepository.save(any(BankAccount.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        Mono<TransactionResponse> result = transactionService.createTransaction("acc-010", request);

        StepVerifier.create(result)
                .expectNextMatches(res -> Double.valueOf(0.0).equals(res.getFee()))
                .verifyComplete();

        verify(accountRepository).save(argThat(acc -> Double.valueOf(1200.0).equals(acc.getBalance())));
    }

    // --- REGLAS DE LA CUENTA A PLAZO FIJO ---

    @Test
    @DisplayName("createTransaction: Plazo fijo rechaza los movimientos que no sean deposito ni retiro")
    void createTransaction_fixedTerm_rejectsTransfers() {
        TransactionRequest request = new TransactionRequest();
        request.setTransactionType("TRANSFER_OUT");
        request.setAmount(100.0);

        when(accountRepository.findById("acc-003")).thenReturn(Mono.just(fixedTermAccount));

        Mono<TransactionResponse> result = transactionService.createTransaction("acc-003", request);

        StepVerifier.create(result)
                .expectErrorMatches(ex -> ex instanceof IllegalArgumentException
                        && ex.getMessage().contains("solo admite movimientos de tipo DEPOSIT o WITHDRAWAL"))
                .verify();

        verifyNoInteractions(transactionRepository);
    }

    @Test
    @DisplayName("createTransaction: Plazo fijo rechaza movimientos fuera del dia habilitado")
    void createTransaction_fixedTerm_wrongDayOfTheMonth_shouldThrowException() {
        BankAccount wrongDayAccount = BankAccount.builder()
                .id("acc-004")
                .accountType(AccountType.FIXED_TERM)
                .balance(5000.0)
                .maintenanceFee(0.0)
                // Elegimos un día distinto al actual para forzar el error
                .fixedTermWithdrawalDay(java.time.LocalDate.now().getDayOfMonth() == 1 ? 2 : 1)
                .build();

        TransactionRequest request = new TransactionRequest();
        request.setTransactionType("DEPOSIT");
        request.setAmount(100.0);

        when(accountRepository.findById("acc-004")).thenReturn(Mono.just(wrongDayAccount));

        Mono<TransactionResponse> result = transactionService.createTransaction("acc-004", request);

        StepVerifier.create(result)
                .expectErrorMatches(ex -> ex instanceof IllegalArgumentException
                        && ex.getMessage().contains("solo permite moverse el día"))
                .verify();

        verifyNoInteractions(transactionRepository);
    }

    @Test
    @DisplayName("createTransaction: Plazo fijo solo permite un movimiento por mes")
    void createTransaction_fixedTerm_secondMovementOfMonth_shouldThrowException() {
        TransactionRequest request = new TransactionRequest();
        request.setTransactionType("DEPOSIT");
        request.setAmount(100.0);

        when(accountRepository.findById("acc-003")).thenReturn(Mono.just(fixedTermAccount));
        when(transactionRepository.countByAccountIdAndTransactionDateBetween(eq("acc-003"), any(), any()))
                .thenReturn(Mono.just(1L)); // Ya movió este mes

        Mono<TransactionResponse> result = transactionService.createTransaction("acc-003", request);

        StepVerifier.create(result)
                .expectErrorMatches(ex -> ex instanceof IllegalArgumentException
                        && ex.getMessage().contains("solo permite un movimiento de retiro o depósito al mes"))
                .verify();

        verify(accountRepository, never()).save(any(BankAccount.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("createTransaction: Plazo fijo registra su unico movimiento mensual el dia habilitado")
    void createTransaction_fixedTerm_firstMovementOfMonth_shouldSucceed() {
        TransactionRequest request = new TransactionRequest();
        request.setTransactionType("WITHDRAWAL");
        request.setAmount(500.0);

        when(accountRepository.findById("acc-003")).thenReturn(Mono.just(fixedTermAccount));
        when(transactionRepository.countByAccountIdAndTransactionDateBetween(eq("acc-003"), any(), any()))
                .thenReturn(Mono.just(0L));
        when(accountRepository.save(any(BankAccount.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        Mono<TransactionResponse> result = transactionService.createTransaction("acc-003", request);

        StepVerifier.create(result)
                .expectNextCount(1)
                .verifyComplete();

        // Sin comision de mantenimiento: 5000 - 500 = 4500
        verify(accountRepository).save(argThat(acc -> Double.valueOf(4500.0).equals(acc.getBalance())));
    }

    // --- CREATE TRANSACTION: WITHDRAWAL ---

    @Test
    @DisplayName("createTransaction: Retiro exitoso disminuye saldo correctamente")
    void createTransaction_withdrawalSuccess_shouldDecreaseBalance() {
        TransactionRequest request = new TransactionRequest();
        request.setTransactionType("WITHDRAWAL");
        request.setAmount(300.0);

        when(accountRepository.findById("acc-001")).thenReturn(Mono.just(sourceAccount));
        when(transactionRepository.countByAccountIdAndTransactionDateBetween(eq("acc-001"), any(), any()))
                .thenReturn(Mono.just(1L));
        when(accountRepository.save(any(BankAccount.class))).thenReturn(Mono.just(sourceAccount));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(Mono.just(transactionEntity));

        Mono<TransactionResponse> result = transactionService.createTransaction("acc-001", request);

        StepVerifier.create(result)
                .expectNextCount(1)
                .verifyComplete();

        // Saldo previo 1000 - 300 = 700
        verify(accountRepository).save(argThat(acc -> Double.valueOf(700.0).equals(acc.getBalance())));
    }

    @Test
    @DisplayName("createTransaction: Retiro falla si el saldo es insuficiente")
    void createTransaction_withdrawalInsufficientBalance_shouldThrowException() {
        TransactionRequest request = new TransactionRequest();
        request.setTransactionType("WITHDRAWAL");
        request.setAmount(1500.0); // Saldo disponible es 1000.0

        when(accountRepository.findById("acc-001")).thenReturn(Mono.just(sourceAccount));
        when(transactionRepository.countByAccountIdAndTransactionDateBetween(eq("acc-001"), any(), any()))
                .thenReturn(Mono.just(1L));

        Mono<TransactionResponse> result = transactionService.createTransaction("acc-001", request);

        StepVerifier.create(result)
                .expectErrorMatches(ex -> ex instanceof IllegalArgumentException
                        && ex.getMessage().contains("Saldo insuficiente"))
                .verify();

        verify(accountRepository, never()).save(any(BankAccount.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    // --- GET TRANSACTIONS BY ACCOUNT ---

    @Test
    @DisplayName("getTransactionsByAccount: Emite historial de movimientos de la cuenta")
    void getTransactionsByAccount_shouldReturnFlux() {
        when(transactionRepository.findByAccountId("acc-001")).thenReturn(Flux.just(transactionEntity));

        Flux<TransactionResponse> result = transactionService.getTransactionsByAccount("acc-001");

        StepVerifier.create(result)
                .expectNextMatches(res -> "tx-001".equals(res.getId())
                        && "acc-001".equals(res.getAccountId()))
                .verifyComplete();

        verify(transactionRepository, times(1)).findByAccountId("acc-001");
    }

    // --- CREATE TRANSFER ---

    @Test
    @DisplayName("createTransfer: Transfiere fondos ejecutando retiro y deposito en cadena")
    void createTransfer_success_shouldChainWithdrawalAndDeposit() {
        TransferRequest request = new TransferRequest();
        request.setSourceAccountId("acc-001");
        request.setTargetAccountId("acc-002");
        request.setAmount(200.0);

        // Mock para cuenta origen (retiro)
        when(accountRepository.findById("acc-001")).thenReturn(Mono.just(sourceAccount));
        when(transactionRepository.countByAccountIdAndTransactionDateBetween(eq("acc-001"), any(), any()))
                .thenReturn(Mono.just(0L));

        // Mock para cuenta destino (depósito)
        when(accountRepository.findById("acc-002")).thenReturn(Mono.just(targetAccount));
        when(transactionRepository.countByAccountIdAndTransactionDateBetween(eq("acc-002"), any(), any()))
                .thenReturn(Mono.just(0L));

        when(accountRepository.save(any(BankAccount.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(transactionRepository.save(any(Transaction.class)))
                .thenReturn(Mono.just(transactionEntity));

        Mono<Void> result = transactionService.createTransfer(request);

        StepVerifier.create(result)
                .verifyComplete();

        // Cuenta origen debe reducir 200 (1000 - 200 = 800)
        verify(accountRepository).save(argThat(acc -> "acc-001".equals(acc.getId()) && Double.valueOf(800.0).equals(acc.getBalance())));
        // Cuenta destino debe incrementar 200 (200 + 200 = 400)
        verify(accountRepository).save(argThat(acc -> "acc-002".equals(acc.getId()) && Double.valueOf(400.0).equals(acc.getBalance())));
    }

    // --- CRUD COMPLETO DE LA ENTIDAD TRANSACTION ---

    @Test
    @DisplayName("findAll: Devuelve todos los movimientos registrados")
    void findAll_shouldReturnFluxOfTransactions() {
        when(transactionRepository.findAll()).thenReturn(Flux.just(transactionEntity));

        StepVerifier.create(transactionService.findAll())
                .expectNextMatches(res -> "tx-001".equals(res.getId()))
                .verifyComplete();

        verify(transactionRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("findById: Devuelve el movimiento cuando el ID existe")
    void findById_whenExists_shouldReturnTransaction() {
        when(transactionRepository.findById("tx-001")).thenReturn(Mono.just(transactionEntity));

        StepVerifier.create(transactionService.findById("tx-001"))
                .expectNextMatches(res -> "tx-001".equals(res.getId()))
                .verifyComplete();
    }

    @Test
    @DisplayName("update: Corrige el tipo y el importe de un movimiento existente")
    void update_whenExists_shouldSaveCorrectedTransaction() {
        TransactionRequest request = new TransactionRequest();
        request.setTransactionType("WITHDRAWAL");
        request.setAmount(250.0);

        when(transactionRepository.findById("tx-001")).thenReturn(Mono.just(transactionEntity));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(transactionService.update("tx-001", request))
                .expectNextMatches(res -> Double.valueOf(250.0).equals(res.getAmount())
                        && "WITHDRAWAL".equalsIgnoreCase(res.getTransactionType()))
                .verifyComplete();

        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }

    @Test
    @DisplayName("update: Rechaza importes no positivos")
    void update_withNotPositiveAmount_shouldThrowException() {
        TransactionRequest request = new TransactionRequest();
        request.setTransactionType("DEPOSIT");
        request.setAmount(-10.0);

        StepVerifier.create(transactionService.update("tx-001", request))
                .expectErrorMatches(ex -> ex instanceof IllegalArgumentException
                        && ex.getMessage().contains("monto del movimiento debe ser mayor que cero"))
                .verify();

        verifyNoInteractions(transactionRepository);
    }

    @Test
    @DisplayName("delete: Invoca la eliminación por ID reactivamente")
    void delete_shouldCallRepositoryDeleteById() {
        when(transactionRepository.deleteById("tx-001")).thenReturn(Mono.empty());

        StepVerifier.create(transactionService.delete("tx-001"))
                .verifyComplete();

        verify(transactionRepository, times(1)).deleteById("tx-001");
    }
}
