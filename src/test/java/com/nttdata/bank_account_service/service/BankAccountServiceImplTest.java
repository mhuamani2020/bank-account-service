package com.nttdata.bank_account_service.service;

import com.nttdata.bank_account_service.client.CreditClient;
import com.nttdata.bank_account_service.client.CustomerClient;
import com.nttdata.bank_account_service.dto.AvailableBalanceResponse;
import com.nttdata.bank_account_service.dto.BankAccountRequest;
import com.nttdata.bank_account_service.dto.BankAccountResponse;
import com.nttdata.bank_account_service.dto.CustomerDTO;
import com.nttdata.bank_account_service.enums.AccountType;
import com.nttdata.bank_account_service.model.BankAccount;
import com.nttdata.bank_account_service.repository.BankAccountRepository;
import com.nttdata.bank_account_service.service.impl.BankAccountServiceImpl;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankAccountServiceImplTest {

    @Mock
    private BankAccountRepository bankAccountRepository;

    @Mock
    private CustomerClient customerClient;

    @Mock
    private CreditClient creditClient;

    @InjectMocks
    private BankAccountServiceImpl bankAccountService;

    private BankAccount sampleAccount;
    private CustomerDTO personalCustomer;
    private CustomerDTO businessCustomer;

    @BeforeEach
    void setUp() {
        sampleAccount = BankAccount.builder()
                .id("acc-001")
                .customerId("cust-personal-01")
                .accountType(AccountType.SAVINGS)
                .balance(300.0)
                .maintenanceFee(0.0)
                .maxMovements(15)
                .build();

        personalCustomer = new CustomerDTO();
        personalCustomer.setId("cust-personal-01");
        personalCustomer.setCustomerType("PERSONAL");

        businessCustomer = new CustomerDTO();
        businessCustomer.setId("cust-business-01");
        businessCustomer.setCustomerType("BUSINESS");
    }

    // --- FIND ALL & FIND BY ID ---

    @Test
    @DisplayName("findAll: Debe emitir lista de cuentas existentes")
    void findAll_shouldReturnFluxOfAccounts() {
        when(bankAccountRepository.findAll()).thenReturn(Flux.just(sampleAccount));

        Flux<BankAccountResponse> result = bankAccountService.findAll();

        StepVerifier.create(result)
                .expectNextMatches(res -> "acc-001".equals(res.getId())
                        && "cust-personal-01".equals(res.getCustomerId()))
                .verifyComplete();

        verify(bankAccountRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("findById: Debe retornar cuenta cuando el ID existe")
    void findById_whenExists_shouldReturnAccount() {
        when(bankAccountRepository.findById("acc-001")).thenReturn(Mono.just(sampleAccount));

        Mono<BankAccountResponse> result = bankAccountService.findById("acc-001");

        StepVerifier.create(result)
                .expectNextMatches(res -> "acc-001".equals(res.getId()))
                .verifyComplete();

        verify(bankAccountRepository, times(1)).findById("acc-001");
    }

    @Test
    @DisplayName("findById: Debe completar vacío cuando el ID no existe")
    void findById_whenNotFound_shouldReturnEmpty() {
        when(bankAccountRepository.findById("missing-id")).thenReturn(Mono.empty());

        Mono<BankAccountResponse> result = bankAccountService.findById("missing-id");

        StepVerifier.create(result)
                .verifyComplete();

        verify(bankAccountRepository, times(1)).findById("missing-id");
    }

    // --- CONSULTA DE SALDO DISPONIBLE ---

    @Test
    @DisplayName("getBalance: Debe devolver el saldo disponible cuando la cuenta existe")
    void getBalance_whenExists_shouldReturnAvailableBalance() {
        when(bankAccountRepository.findById("acc-001")).thenReturn(Mono.just(sampleAccount));

        StepVerifier.create(bankAccountService.getBalance("acc-001"))
                .expectNextMatches(balance -> "acc-001".equals(balance.getAccountId())
                        && "cust-personal-01".equals(balance.getCustomerId())
                        && "SAVINGS".equals(balance.getAccountType())
                        && Double.valueOf(300.0).equals(balance.getBalance()))
                .verifyComplete();

        verify(bankAccountRepository, times(1)).findById("acc-001");
    }

    @Test
    @DisplayName("getBalance: Debe completar vacío cuando la cuenta no existe")
    void getBalance_whenNotFound_shouldReturnEmpty() {
        when(bankAccountRepository.findById("missing-id")).thenReturn(Mono.empty());

        StepVerifier.create(bankAccountService.getBalance("missing-id"))
                .verifyComplete();
    }

    // --- CREATE: VALIDACIONES DE CLIENTE Y REGLAS DE NEGOCIO ---

    @Test
    @DisplayName("create: Debe lanzar error si el cliente no existe")
    void create_whenCustomerNotFound_shouldThrowException() {
        BankAccountRequest request = new BankAccountRequest();
        request.setCustomerId("unknown-cust");
        request.setAccountType("SAVINGS");

        when(customerClient.getCustomerById("unknown-cust")).thenReturn(Mono.empty());

        Mono<BankAccountResponse> result = bankAccountService.create(request);

        StepVerifier.create(result)
                .expectErrorMatches(throwable -> throwable instanceof IllegalArgumentException
                        && "El cliente no existe".equals(throwable.getMessage()))
                .verify();

        verify(customerClient, times(1)).getCustomerById("unknown-cust");
        verifyNoInteractions(bankAccountRepository);
    }

    @Test
    @DisplayName("create: Cliente PERSONAL crea cuenta SAVINGS exitosamente cuando no tiene previa")
    void create_personalCustomer_savingsSuccess() {
        BankAccountRequest request = new BankAccountRequest();
        request.setCustomerId("cust-personal-01");
        request.setAccountType("SAVINGS");
        request.setBalance(100.0);
        request.setMaintenanceFee(0.0);
        request.setMaxMovements(10);

        when(customerClient.getCustomerById("cust-personal-01")).thenReturn(Mono.just(personalCustomer));
        when(creditClient.customerHasOverdueDebt("cust-personal-01")).thenReturn(Mono.just(false));
        when(bankAccountRepository.countByCustomerIdAndAccountType("cust-personal-01", "SAVINGS"))
                .thenReturn(Mono.just(0L));
        when(bankAccountRepository.save(any(BankAccount.class))).thenReturn(Mono.just(sampleAccount));

        Mono<BankAccountResponse> result = bankAccountService.create(request);

        StepVerifier.create(result)
                .expectNextMatches(res -> "acc-001".equals(res.getId()))
                .verifyComplete();

        verify(bankAccountRepository, times(1)).save(any(BankAccount.class));
    }

    @Test
    @DisplayName("create: Cliente PERSONAL falla si ya tiene una cuenta del mismo tipo")
    void create_personalCustomer_duplicateAccountType_shouldFail() {
        BankAccountRequest request = new BankAccountRequest();
        request.setCustomerId("cust-personal-01");
        request.setAccountType("SAVINGS");

        when(customerClient.getCustomerById("cust-personal-01")).thenReturn(Mono.just(personalCustomer));
        when(creditClient.customerHasOverdueDebt("cust-personal-01")).thenReturn(Mono.just(false));
        when(bankAccountRepository.countByCustomerIdAndAccountType("cust-personal-01", "SAVINGS"))
                .thenReturn(Mono.just(1L));

        Mono<BankAccountResponse> result = bankAccountService.create(request);

        StepVerifier.create(result)
                .expectErrorMatches(throwable -> throwable instanceof IllegalArgumentException
                        && throwable.getMessage().contains("máximo de una cuenta de tipo SAVINGS"))
                .verify();

        verify(bankAccountRepository, never()).save(any(BankAccount.class));
    }

    @Test
    @DisplayName("create: Cliente BUSINESS no puede abrir cuenta SAVINGS")
    void create_businessCustomer_savings_shouldFail() {
        BankAccountRequest request = new BankAccountRequest();
        request.setCustomerId("cust-business-01");
        request.setAccountType("SAVINGS");

        when(customerClient.getCustomerById("cust-business-01")).thenReturn(Mono.just(businessCustomer));
        when(creditClient.customerHasOverdueDebt("cust-business-01")).thenReturn(Mono.just(false));

        Mono<BankAccountResponse> result = bankAccountService.create(request);

        StepVerifier.create(result)
                .expectErrorMatches(throwable -> throwable instanceof IllegalArgumentException
                        && throwable.getMessage().contains("Un cliente empresarial no puede tener cuentas de ahorro ni a plazo fijo"))
                .verify();

        verify(bankAccountRepository, never()).save(any(BankAccount.class));
    }

    @Test
    @DisplayName("create: Cliente BUSINESS puede abrir cuenta CURRENT si indica comision y al menos un titular")
    void create_businessCustomer_currentAccount_success() {
        BankAccountRequest request = new BankAccountRequest();
        request.setCustomerId("cust-business-01");
        request.setAccountType("CURRENT");
        request.setBalance(1500.0);
        request.setMaintenanceFee(30.0);
        request.setHolders(List.of("Empresa Andes SAC"));

        BankAccount currentAccount = BankAccount.builder()
                .id("acc-biz-01")
                .customerId("cust-business-01")
                .accountType(AccountType.CURRENT)
                .balance(1500.0)
                .maintenanceFee(30.0)
                .holders(List.of("Empresa Andes SAC"))
                .build();

        when(customerClient.getCustomerById("cust-business-01")).thenReturn(Mono.just(businessCustomer));
        when(creditClient.customerHasOverdueDebt("cust-business-01")).thenReturn(Mono.just(false));
        when(bankAccountRepository.save(any(BankAccount.class))).thenReturn(Mono.just(currentAccount));

        Mono<BankAccountResponse> result = bankAccountService.create(request);

        StepVerifier.create(result)
                .expectNextMatches(res -> "acc-biz-01".equals(res.getId()))
                .verifyComplete();

        verify(bankAccountRepository, times(1)).save(any(BankAccount.class));
    }

    @Test
    @DisplayName("create: Cliente BUSINESS sin titulares lanza error de negocio")
    void create_businessCustomer_withoutHolders_shouldFail() {
        BankAccountRequest request = new BankAccountRequest();
        request.setCustomerId("cust-business-01");
        request.setAccountType("CURRENT");
        request.setBalance(1500.0);
        request.setMaintenanceFee(30.0);

        when(customerClient.getCustomerById("cust-business-01")).thenReturn(Mono.just(businessCustomer));
        when(creditClient.customerHasOverdueDebt("cust-business-01")).thenReturn(Mono.just(false));

        Mono<BankAccountResponse> result = bankAccountService.create(request);

        StepVerifier.create(result)
                .expectErrorMatches(ex -> ex instanceof IllegalArgumentException
                        && ex.getMessage().contains("al menos un titular"))
                .verify();

        verify(bankAccountRepository, never()).save(any(BankAccount.class));
    }

    @Test
    @DisplayName("create: Cuenta CORRIENTE sin comision de mantenimiento lanza error de negocio")
    void create_currentAccountWithoutMaintenanceFee_shouldFail() {
        BankAccountRequest request = new BankAccountRequest();
        request.setCustomerId("cust-personal-01");
        request.setAccountType("CURRENT");
        request.setBalance(800.0);

        when(customerClient.getCustomerById("cust-personal-01")).thenReturn(Mono.just(personalCustomer));
        when(creditClient.customerHasOverdueDebt("cust-personal-01")).thenReturn(Mono.just(false));
        when(bankAccountRepository.countByCustomerIdAndAccountType("cust-personal-01", "CURRENT"))
                .thenReturn(Mono.just(0L));

        Mono<BankAccountResponse> result = bankAccountService.create(request);

        StepVerifier.create(result)
                .expectErrorMatches(ex -> ex instanceof IllegalArgumentException
                        && ex.getMessage().contains("comisión de mantenimiento mayor que cero"))
                .verify();

        verify(bankAccountRepository, never()).save(any(BankAccount.class));
    }

    @Test
    @DisplayName("create: Cuenta de AHORRO sin limite mensual de movimientos lanza error de negocio")
    void create_savingsWithoutMaxMovements_shouldFail() {
        BankAccountRequest request = new BankAccountRequest();
        request.setCustomerId("cust-personal-01");
        request.setAccountType("SAVINGS");
        request.setBalance(100.0);

        when(customerClient.getCustomerById("cust-personal-01")).thenReturn(Mono.just(personalCustomer));
        when(creditClient.customerHasOverdueDebt("cust-personal-01")).thenReturn(Mono.just(false));
        when(bankAccountRepository.countByCustomerIdAndAccountType("cust-personal-01", "SAVINGS"))
                .thenReturn(Mono.just(0L));

        Mono<BankAccountResponse> result = bankAccountService.create(request);

        StepVerifier.create(result)
                .expectErrorMatches(ex -> ex instanceof IllegalArgumentException
                        && ex.getMessage().contains("límite máximo de movimientos mensuales"))
                .verify();

        verify(bankAccountRepository, never()).save(any(BankAccount.class));
    }

    @Test
    @DisplayName("create: Cuenta de AHORRO siempre queda libre de comision de mantenimiento")
    void create_savings_freesMaintenanceFeeAndKeepsMovementLimit() {
        BankAccountRequest request = new BankAccountRequest();
        request.setCustomerId("cust-personal-01");
        request.setAccountType("SAVINGS");
        request.setBalance(100.0);
        request.setMaintenanceFee(99.0); // Intento de comisión: se debe ignorar
        request.setMaxMovements(10);

        when(customerClient.getCustomerById("cust-personal-01")).thenReturn(Mono.just(personalCustomer));
        when(creditClient.customerHasOverdueDebt("cust-personal-01")).thenReturn(Mono.just(false));
        when(bankAccountRepository.countByCustomerIdAndAccountType("cust-personal-01", "SAVINGS"))
                .thenReturn(Mono.just(0L));
        when(bankAccountRepository.save(any(BankAccount.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        Mono<BankAccountResponse> result = bankAccountService.create(request);

        StepVerifier.create(result)
                .expectNextMatches(res -> Double.valueOf(0.0).equals(res.getMaintenanceFee())
                        && Integer.valueOf(10).equals(res.getMaxMovements()))
                .verifyComplete();

        verify(bankAccountRepository).save(argThat(acc ->
                Double.valueOf(0.0).equals(acc.getMaintenanceFee())
                        && Integer.valueOf(10).equals(acc.getMaxMovements())));
    }

    @Test
    @DisplayName("create: Cuenta CORRIENTE se registra sin limite de movimientos mensuales")
    void create_currentAccount_forcesNoMovementLimit() {
        BankAccountRequest request = new BankAccountRequest();
        request.setCustomerId("cust-personal-01");
        request.setAccountType("CURRENT");
        request.setBalance(800.0);
        request.setMaintenanceFee(25.0);
        request.setMaxMovements(50); // Se debe descartar: la corriente no tiene límite

        when(customerClient.getCustomerById("cust-personal-01")).thenReturn(Mono.just(personalCustomer));
        when(creditClient.customerHasOverdueDebt("cust-personal-01")).thenReturn(Mono.just(false));
        when(bankAccountRepository.countByCustomerIdAndAccountType("cust-personal-01", "CURRENT"))
                .thenReturn(Mono.just(0L));
        when(bankAccountRepository.save(any(BankAccount.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        Mono<BankAccountResponse> result = bankAccountService.create(request);

        StepVerifier.create(result)
                .expectNextMatches(res -> res.getMaxMovements() == null
                        && Double.valueOf(25.0).equals(res.getMaintenanceFee()))
                .verifyComplete();

        verify(bankAccountRepository).save(argThat(acc -> acc.getMaxMovements() == null));
    }

    @Test
    @DisplayName("create: Cuenta a PLAZO FIJO queda libre de comision y con dia habilitado")
    void create_fixedTermAccount_isFreeOfFeeAndHasWithdrawalDay() {
        BankAccountRequest request = new BankAccountRequest();
        request.setCustomerId("cust-personal-01");
        request.setAccountType("FIXED_TERM");
        request.setBalance(5000.0);
        request.setMaintenanceFee(40.0); // Se debe ignorar

        when(customerClient.getCustomerById("cust-personal-01")).thenReturn(Mono.just(personalCustomer));
        when(creditClient.customerHasOverdueDebt("cust-personal-01")).thenReturn(Mono.just(false));
        when(bankAccountRepository.countByCustomerIdAndAccountType("cust-personal-01", "FIXED_TERM"))
                .thenReturn(Mono.just(0L));
        when(bankAccountRepository.save(any(BankAccount.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        Mono<BankAccountResponse> result = bankAccountService.create(request);

        StepVerifier.create(result)
                .expectNextMatches(res -> Double.valueOf(0.0).equals(res.getMaintenanceFee())
                        && res.getFixedTermWithdrawalDay() != null
                        && res.getFixedTermWithdrawalDay() >= 1
                        && res.getFixedTermWithdrawalDay() <= 31)
                .verifyComplete();

        verify(bankAccountRepository).save(argThat(acc ->
                Double.valueOf(0.0).equals(acc.getMaintenanceFee())
                        && Integer.valueOf(1).equals(acc.getFixedTermWithdrawalDay())));
    }

    @Test
    @DisplayName("create: Cuenta VIP falla si el cliente no tiene tarjeta de credito")
    void create_vipAccount_withoutCreditCard_shouldFail() {
        BankAccountRequest request = new BankAccountRequest();
        request.setCustomerId("cust-personal-01");
        request.setAccountType("VIP");

        when(customerClient.getCustomerById("cust-personal-01")).thenReturn(Mono.just(personalCustomer));
        when(creditClient.customerHasOverdueDebt("cust-personal-01")).thenReturn(Mono.just(false));
        when(creditClient.customerHasCreditCard("cust-personal-01")).thenReturn(Mono.just(false));

        Mono<BankAccountResponse> result = bankAccountService.create(request);

        StepVerifier.create(result)
                .expectErrorMatches(throwable -> throwable instanceof IllegalArgumentException
                        && throwable.getMessage().contains("el cliente debe poseer una tarjeta de crédito"))
                .verify();

        verify(creditClient, times(1)).customerHasCreditCard("cust-personal-01");
        verify(bankAccountRepository, never()).save(any(BankAccount.class));
    }

    @Test
    @DisplayName("create: Cuenta VIP tiene éxito si el cliente tiene tarjeta de credito y cumple reglas")
    void create_vipAccount_withCreditCard_success() {
        BankAccountRequest request = new BankAccountRequest();
        request.setCustomerId("cust-personal-01");
        request.setAccountType("VIP");
        request.setBalance(500.0);

        BankAccount vipAccount = BankAccount.builder()
                .id("acc-vip-01")
                .customerId("cust-personal-01")
                .accountType(AccountType.VIP)
                .balance(500.0)
                .build();

        when(customerClient.getCustomerById("cust-personal-01")).thenReturn(Mono.just(personalCustomer));
        when(creditClient.customerHasOverdueDebt("cust-personal-01")).thenReturn(Mono.just(false));
        when(creditClient.customerHasCreditCard("cust-personal-01")).thenReturn(Mono.just(true));
        when(bankAccountRepository.countByCustomerIdAndAccountType("cust-personal-01", "VIP"))
                .thenReturn(Mono.just(0L));
        when(bankAccountRepository.save(any(BankAccount.class))).thenReturn(Mono.just(vipAccount));

        Mono<BankAccountResponse> result = bankAccountService.create(request);

        StepVerifier.create(result)
                .expectNextMatches(res -> "acc-vip-01".equals(res.getId()))
                .verifyComplete();

        verify(bankAccountRepository, times(1)).save(any(BankAccount.class));
    }

    // --- REQUERIMIENTO PARTE III: BLOQUEO POR DEUDA VENCIDA ---

    @Test
    @DisplayName("create: Cliente con deuda vencida no puede adquirir una cuenta")
    void create_customerWithOverdueDebt_shouldFail() {
        BankAccountRequest request = new BankAccountRequest();
        request.setCustomerId("cust-personal-01");
        request.setAccountType("SAVINGS");
        request.setBalance(100.0);
        request.setMaxMovements(10);

        when(customerClient.getCustomerById("cust-personal-01")).thenReturn(Mono.just(personalCustomer));
        when(creditClient.customerHasOverdueDebt("cust-personal-01")).thenReturn(Mono.just(true));

        Mono<BankAccountResponse> result = bankAccountService.create(request);

        StepVerifier.create(result)
                .expectErrorMatches(throwable -> throwable instanceof IllegalArgumentException
                        && throwable.getMessage().contains("deuda vencida"))
                .verify();

        verify(bankAccountRepository, never()).save(any(BankAccount.class));
    }

    // --- REQUERIMIENTO PARTE II: MONTO MÍNIMO DE APERTURA ---

    @Test
    @DisplayName("create: Falla si el saldo inicial no alcanza el monto mínimo de apertura")
    void create_balanceBelowMinimumOpeningAmount_shouldFail() {
        org.springframework.test.util.ReflectionTestUtils.setField(
                bankAccountService, "minimumOpeningAmount", 100.0);

        BankAccountRequest request = new BankAccountRequest();
        request.setCustomerId("cust-personal-01");
        request.setAccountType("SAVINGS");
        request.setBalance(50.0); // Por debajo del mínimo de 100
        request.setMaxMovements(10);

        when(customerClient.getCustomerById("cust-personal-01")).thenReturn(Mono.just(personalCustomer));
        when(creditClient.customerHasOverdueDebt("cust-personal-01")).thenReturn(Mono.just(false));
        when(bankAccountRepository.countByCustomerIdAndAccountType("cust-personal-01", "SAVINGS"))
                .thenReturn(Mono.just(0L));

        Mono<BankAccountResponse> result = bankAccountService.create(request);

        StepVerifier.create(result)
                .expectErrorMatches(ex -> ex instanceof IllegalArgumentException
                        && ex.getMessage().contains("monto mínimo de apertura"))
                .verify();

        verify(bankAccountRepository, never()).save(any(BankAccount.class));
    }

    @Test
    @DisplayName("create: Éxito si el saldo inicial alcanza el monto mínimo de apertura")
    void create_balanceReachesMinimumOpeningAmount_success() {
        org.springframework.test.util.ReflectionTestUtils.setField(
                bankAccountService, "minimumOpeningAmount", 100.0);

        BankAccountRequest request = new BankAccountRequest();
        request.setCustomerId("cust-personal-01");
        request.setAccountType("SAVINGS");
        request.setBalance(150.0);
        request.setMaxMovements(10);

        when(customerClient.getCustomerById("cust-personal-01")).thenReturn(Mono.just(personalCustomer));
        when(creditClient.customerHasOverdueDebt("cust-personal-01")).thenReturn(Mono.just(false));
        when(bankAccountRepository.countByCustomerIdAndAccountType("cust-personal-01", "SAVINGS"))
                .thenReturn(Mono.just(0L));
        when(bankAccountRepository.save(any(BankAccount.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(bankAccountService.create(request))
                .expectNextCount(1)
                .verifyComplete();

        verify(bankAccountRepository, times(1)).save(any(BankAccount.class));
    }

    // --- UPDATE & DELETE ---

    @Test
    @DisplayName("update: Actualiza campos y guarda correctamente")
    void update_whenAccountExists_shouldSaveUpdatedData() {
        BankAccountRequest updateReq = new BankAccountRequest();
        updateReq.setAccountType("CURRENT");
        updateReq.setBalance(850.0);
        updateReq.setMaintenanceFee(10.0);
        updateReq.setMaxMovements(30);

        when(bankAccountRepository.findById("acc-001")).thenReturn(Mono.just(sampleAccount));
        when(customerClient.getCustomerById("cust-personal-01")).thenReturn(Mono.just(personalCustomer));
        when(bankAccountRepository.save(any(BankAccount.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        Mono<BankAccountResponse> result = bankAccountService.update("acc-001", updateReq);

        StepVerifier.create(result)
                .expectNextMatches(res -> Double.valueOf(850.0).equals(res.getBalance())
                        && "CURRENT".equalsIgnoreCase(res.getAccountType())
                        // Regla de producto aplicada también en la actualización:
                        // la cuenta corriente no admite límite de movimientos
                        && res.getMaxMovements() == null)
                .verifyComplete();

        verify(bankAccountRepository, times(1)).findById("acc-001");
        verify(bankAccountRepository, times(1)).save(any(BankAccount.class));
    }

    @Test
    @DisplayName("delete: Invoca eliminación por ID reactivamente")
    void delete_shouldCallRepositoryDelete() {
        when(bankAccountRepository.deleteById("acc-001")).thenReturn(Mono.empty());

        Mono<Void> result = bankAccountService.delete("acc-001");

        StepVerifier.create(result)
                .verifyComplete();

        verify(bankAccountRepository, times(1)).deleteById("acc-001");
    }
}