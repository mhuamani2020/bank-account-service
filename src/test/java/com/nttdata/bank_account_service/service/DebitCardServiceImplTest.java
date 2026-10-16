package com.nttdata.bank_account_service.service;

import com.nttdata.bank_account_service.dto.DebitCardPaymentRequest;
import com.nttdata.bank_account_service.dto.DebitCardPaymentResponse;
import com.nttdata.bank_account_service.dto.DebitCardRequest;
import com.nttdata.bank_account_service.dto.DebitCardResponse;
import com.nttdata.bank_account_service.dto.TransactionRequest;
import com.nttdata.bank_account_service.dto.TransactionResponse;
import com.nttdata.bank_account_service.enums.AccountType;
import com.nttdata.bank_account_service.enums.DebitCardStatus;
import com.nttdata.bank_account_service.enums.TransactionType;
import com.nttdata.bank_account_service.model.BankAccount;
import com.nttdata.bank_account_service.model.DebitCard;
import com.nttdata.bank_account_service.model.Transaction;
import com.nttdata.bank_account_service.repository.BankAccountRepository;
import com.nttdata.bank_account_service.repository.DebitCardRepository;
import com.nttdata.bank_account_service.repository.TransactionRepository;
import com.nttdata.bank_account_service.service.impl.DebitCardServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de {@link DebitCardServiceImpl} con mocks de los repositorios y del
 * servicio de movimientos (Parte III: las nuevas funcionalidades deben tener sus pruebas
 * unitarias con mocks).
 *
 * @author NTT Data
 */
@ExtendWith(MockitoExtension.class)
class DebitCardServiceImplTest {

    @Mock
    private DebitCardRepository debitCardRepository;

    @Mock
    private BankAccountRepository bankAccountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionService transactionService;

    @InjectMocks
    private DebitCardServiceImpl debitCardService;

    private BankAccount savingsAccount;
    private BankAccount fixedTermAccount;
    private DebitCard activeCard;

    @BeforeEach
    void setUp() {
        savingsAccount = BankAccount.builder()
                .id("acc-001")
                .customerId("cust-01")
                .accountType(AccountType.SAVINGS)
                .balance(500.0)
                .maintenanceFee(0.0)
                .maxMovements(15)
                .build();

        fixedTermAccount = BankAccount.builder()
                .id("acc-fixed")
                .customerId("cust-01")
                .accountType(AccountType.FIXED_TERM)
                .balance(1000.0)
                .build();

        activeCard = DebitCard.builder()
                .id("card-01")
                .accountId("acc-001")
                .customerId("cust-01")
                .cardNumber("4123456789012345")
                .status(DebitCardStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // --- CREATE ---

    @Test
    @DisplayName("create: Emite una tarjeta cuando la cuenta existe y no tiene tarjeta")
    void create_whenValid_shouldEmitCard() {
        DebitCardRequest request = new DebitCardRequest();
        request.setAccountId("acc-001");

        when(bankAccountRepository.findById("acc-001")).thenReturn(Mono.just(savingsAccount));
        when(debitCardRepository.existsByAccountId("acc-001")).thenReturn(Mono.just(false));
        when(debitCardRepository.save(any(DebitCard.class))).thenAnswer(invocation -> {
            DebitCard card = invocation.getArgument(0);
            card.setId("card-new");
            return Mono.just(card);
        });

        StepVerifier.create(debitCardService.create(request).toFlowable())
                .assertNext(response -> {
                    assertThat(response.getId()).isEqualTo("card-new");
                    assertThat(response.getAccountId()).isEqualTo("acc-001");
                    assertThat(response.getCustomerId()).isEqualTo("cust-01");
                    assertThat(response.getCardNumber()).startsWith("4").hasSize(16);
                    assertThat(response.getStatus()).isEqualTo("ACTIVE");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("create: Falla si el identificador de la cuenta es nulo o vacío")
    void create_whenAccountIdMissing_shouldFail() {
        DebitCardRequest request = new DebitCardRequest();

        StepVerifier.create(debitCardService.create(request).toFlowable())
                .expectErrorMatches(ex -> ex instanceof IllegalArgumentException
                        && ex.getMessage().contains("cuenta es obligatorio"))
                .verify();

        verify(debitCardRepository, never()).save(any(DebitCard.class));
    }

    @Test
    @DisplayName("create: Falla si la cuenta no existe")
    void create_whenAccountNotExists_shouldFail() {
        DebitCardRequest request = new DebitCardRequest();
        request.setAccountId("acc-999");

        when(bankAccountRepository.findById("acc-999")).thenReturn(Mono.empty());

        StepVerifier.create(debitCardService.create(request).toFlowable())
                .expectErrorMatches(ex -> ex instanceof IllegalArgumentException
                        && ex.getMessage().contains("cuenta bancaria no existe"))
                .verify();
    }

    @Test
    @DisplayName("create: Falla para cuentas a plazo fijo")
    void create_whenFixedTermAccount_shouldFail() {
        DebitCardRequest request = new DebitCardRequest();
        request.setAccountId("acc-fixed");

        when(bankAccountRepository.findById("acc-fixed")).thenReturn(Mono.just(fixedTermAccount));

        StepVerifier.create(debitCardService.create(request).toFlowable())
                .expectErrorMatches(ex -> ex instanceof IllegalArgumentException
                        && ex.getMessage().contains("plazo fijo"))
                .verify();

        verify(debitCardRepository, never()).save(any(DebitCard.class));
    }

    @Test
    @DisplayName("create: Falla si la cuenta ya tiene una tarjeta de débito")
    void create_whenAccountAlreadyHasCard_shouldFail() {
        DebitCardRequest request = new DebitCardRequest();
        request.setAccountId("acc-001");

        when(bankAccountRepository.findById("acc-001")).thenReturn(Mono.just(savingsAccount));
        when(debitCardRepository.existsByAccountId("acc-001")).thenReturn(Mono.just(true));

        StepVerifier.create(debitCardService.create(request).toFlowable())
                .expectErrorMatches(ex -> ex instanceof IllegalArgumentException
                        && ex.getMessage().contains("ya tiene una tarjeta"))
                .verify();
    }

    @Test
    @DisplayName("create: Falla si el estado enviado no es válido")
    void create_whenInvalidStatus_shouldFail() {
        DebitCardRequest request = new DebitCardRequest();
        request.setAccountId("acc-001");
        request.setStatus("INVALID");

        when(bankAccountRepository.findById("acc-001")).thenReturn(Mono.just(savingsAccount));
        when(debitCardRepository.existsByAccountId("acc-001")).thenReturn(Mono.just(false));

        StepVerifier.create(debitCardService.create(request).toFlowable())
                .expectErrorMatches(ex -> ex instanceof IllegalArgumentException
                        && ex.getMessage().contains("Estado de tarjeta inválido"))
                .verify();
    }

    @Test
    @DisplayName("create: Respeta el estado BLOCKED enviado en la petición")
    void create_whenBlockedRequested_shouldPersistBlocked() {
        DebitCardRequest request = new DebitCardRequest();
        request.setAccountId("acc-001");
        request.setStatus("blocked");

        when(bankAccountRepository.findById("acc-001")).thenReturn(Mono.just(savingsAccount));
        when(debitCardRepository.existsByAccountId("acc-001")).thenReturn(Mono.just(false));
        when(debitCardRepository.save(any(DebitCard.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(debitCardService.create(request).toFlowable())
                .assertNext(response -> assertThat(response.getStatus()).isEqualTo("BLOCKED"))
                .verifyComplete();
    }

    // --- FIND ALL / FIND BY ID / FIND BY CUSTOMER ---

    @Test
    @DisplayName("findAll: Devuelve todas las tarjetas")
    void findAll_shouldReturnCards() {
        when(debitCardRepository.findAll()).thenReturn(Flux.just(activeCard));

        StepVerifier.create(debitCardService.findAll())
                .assertNext(response -> assertThat(response.getId()).isEqualTo("card-01"))
                .verifyComplete();
    }

    @Test
    @DisplayName("findById: Devuelve la tarjeta cuando existe")
    void findById_whenExists_shouldReturnCard() {
        when(debitCardRepository.findById("card-01")).thenReturn(Mono.just(activeCard));

        StepVerifier.create(debitCardService.findById("card-01").toFlowable())
                .assertNext(response -> assertThat(response.getCardNumber()).isEqualTo("4123456789012345"))
                .verifyComplete();
    }

    @Test
    @DisplayName("findById: Devuelve vacío cuando no existe")
    void findById_whenNotFound_shouldReturnEmpty() {
        when(debitCardRepository.findById("card-999")).thenReturn(Mono.empty());

        StepVerifier.create(debitCardService.findById("card-999").toFlowable()).verifyComplete();
    }

    @Test
    @DisplayName("findByCustomer: Devuelve las tarjetas del cliente")
    void findByCustomer_shouldReturnCards() {
        when(debitCardRepository.findByCustomerId("cust-01")).thenReturn(Flux.just(activeCard));

        StepVerifier.create(debitCardService.findByCustomer("cust-01"))
                .assertNext(response -> assertThat(response.getCustomerId()).isEqualTo("cust-01"))
                .verifyComplete();
    }

    // --- UPDATE / DELETE ---

    @Test
    @DisplayName("update: Cambia el estado de la tarjeta")
    void update_shouldChangeStatus() {
        DebitCardRequest request = new DebitCardRequest();
        request.setStatus("BLOCKED");

        when(debitCardRepository.findById("card-01")).thenReturn(Mono.just(activeCard));
        when(debitCardRepository.save(any(DebitCard.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(debitCardService.update("card-01", request).toFlowable())
                .assertNext(response -> assertThat(response.getStatus()).isEqualTo("BLOCKED"))
                .verifyComplete();
    }

    @Test
    @DisplayName("update: Devuelve vacío si la tarjeta no existe")
    void update_whenNotFound_shouldReturnEmpty() {
        when(debitCardRepository.findById("card-999")).thenReturn(Mono.empty());

        StepVerifier.create(debitCardService.update("card-999", new DebitCardRequest()).toFlowable()).verifyComplete();
    }

    @Test
    @DisplayName("delete: Elimina la tarjeta existente")
    void delete_whenExists_shouldDelete() {
        when(debitCardRepository.findById("card-01")).thenReturn(Mono.just(activeCard));
        when(debitCardRepository.delete(activeCard)).thenReturn(Mono.empty());

        StepVerifier.create(debitCardService.delete("card-01").toFlowable()).verifyComplete();

        verify(debitCardRepository).delete(activeCard);
    }

    @Test
    @DisplayName("delete: No falla si la tarjeta no existe")
    void delete_whenNotFound_shouldComplete() {
        when(debitCardRepository.findById("card-999")).thenReturn(Mono.empty());

        StepVerifier.create(debitCardService.delete("card-999").toFlowable()).verifyComplete();

        verify(debitCardRepository, never()).delete(any(DebitCard.class));
    }

    // --- PAY ---

    @Test
    @DisplayName("pay: Debita la cuenta y registra el movimiento de la tarjeta")
    void pay_whenActiveCard_shouldDebitAccount() {
        DebitCardPaymentRequest request = new DebitCardPaymentRequest();
        request.setAmount(100.0);

        TransactionResponse transaction = new TransactionResponse();
        transaction.setId("tx-01");
        transaction.setAccountId("acc-001");
        transaction.setTransactionType("DEBIT_CARD_PAYMENT");
        transaction.setAmount(100.0);
        transaction.setFee(0.0);
        transaction.setTransactionDate(java.time.OffsetDateTime.now());

        BankAccount debitedAccount = BankAccount.builder()
                .id("acc-001").customerId("cust-01").accountType(AccountType.SAVINGS).balance(400.0).build();

        when(debitCardRepository.findById("card-01")).thenReturn(Mono.just(activeCard));
        when(transactionService.createTransaction(eq("acc-001"), any(TransactionRequest.class)))
                .thenReturn(Mono.just(transaction));
        when(bankAccountRepository.findById("acc-001")).thenReturn(Mono.just(debitedAccount));

        StepVerifier.create(debitCardService.pay("card-01", request).toFlowable())
                .assertNext(response -> {
                    assertThat(response.getCardId()).isEqualTo("card-01");
                    assertThat(response.getAccountId()).isEqualTo("acc-001");
                    assertThat(response.getTransactionId()).isEqualTo("tx-01");
                    assertThat(response.getBalanceAfter()).isEqualTo(400.0);
                })
                .verifyComplete();

        // Verifica que el movimiento se registró como pago con tarjeta y con el id de la tarjeta
        ArgumentCaptor<TransactionRequest> captor = ArgumentCaptor.forClass(TransactionRequest.class);
        verify(transactionService).createTransaction(eq("acc-001"), captor.capture());
        assertThat(captor.getValue().getTransactionType())
                .isEqualTo(TransactionType.DEBIT_CARD_PAYMENT.name());
        assertThat(captor.getValue().getDebitCardId()).isEqualTo("card-01");
    }

    @Test
    @DisplayName("pay: Falla si el importe no es mayor que cero")
    void pay_whenInvalidAmount_shouldFail() {
        DebitCardPaymentRequest request = new DebitCardPaymentRequest();
        request.setAmount(0.0);

        StepVerifier.create(debitCardService.pay("card-01", request).toFlowable())
                .expectErrorMatches(ex -> ex instanceof IllegalArgumentException
                        && ex.getMessage().contains("mayor que cero"))
                .verify();

        verify(transactionService, never()).createTransaction(any(), any());
    }

    @Test
    @DisplayName("pay: Falla si la tarjeta no existe")
    void pay_whenCardNotExists_shouldFail() {
        DebitCardPaymentRequest request = new DebitCardPaymentRequest();
        request.setAmount(50.0);

        when(debitCardRepository.findById("card-999")).thenReturn(Mono.empty());

        StepVerifier.create(debitCardService.pay("card-999", request).toFlowable())
                .expectErrorMatches(ex -> ex instanceof IllegalArgumentException
                        && ex.getMessage().contains("tarjeta de débito no existe"))
                .verify();
    }

    @Test
    @DisplayName("pay: Falla si la tarjeta está bloqueada")
    void pay_whenCardBlocked_shouldFail() {
        activeCard.setStatus(DebitCardStatus.BLOCKED);
        DebitCardPaymentRequest request = new DebitCardPaymentRequest();
        request.setAmount(50.0);

        when(debitCardRepository.findById("card-01")).thenReturn(Mono.just(activeCard));

        StepVerifier.create(debitCardService.pay("card-01", request).toFlowable())
                .expectErrorMatches(ex -> ex instanceof IllegalArgumentException
                        && ex.getMessage().contains("no está activa"))
                .verify();

        verify(transactionService, never()).createTransaction(any(), any());
    }

    @Test
    @DisplayName("pay: Propaga el error de saldo insuficiente del servicio de movimientos")
    void pay_whenInsufficientBalance_shouldPropagateError() {
        DebitCardPaymentRequest request = new DebitCardPaymentRequest();
        request.setAmount(10_000.0);

        when(debitCardRepository.findById("card-01")).thenReturn(Mono.just(activeCard));
        when(transactionService.createTransaction(eq("acc-001"), any(TransactionRequest.class)))
                .thenReturn(Mono.error(new IllegalArgumentException("Saldo insuficiente")));

        StepVerifier.create(debitCardService.pay("card-01", request).toFlowable())
                .expectErrorMatches(ex -> ex instanceof IllegalArgumentException
                        && ex.getMessage().contains("Saldo insuficiente"))
                .verify();
    }

    // --- MOVEMENTS ---

    @Test
    @DisplayName("getMovements: Devuelve los movimientos de la tarjeta")
    void getMovements_shouldReturnTransactions() {
        Transaction transaction = Transaction.builder()
                .id("tx-01")
                .accountId("acc-001")
                .transactionType(TransactionType.DEBIT_CARD_PAYMENT)
                .amount(100.0)
                .fee(0.0)
                .debitCardId("card-01")
                .transactionDate(LocalDateTime.now())
                .build();

        when(transactionRepository.findByDebitCardIdOrderByTransactionDateDesc("card-01"))
                .thenReturn(Flux.just(transaction));

        StepVerifier.create(debitCardService.getMovements("card-01"))
                .assertNext(response -> {
                    assertThat(response.getId()).isEqualTo("tx-01");
                    assertThat(response.getDebitCardId()).isEqualTo("card-01");
                })
                .verifyComplete();
    }
}
