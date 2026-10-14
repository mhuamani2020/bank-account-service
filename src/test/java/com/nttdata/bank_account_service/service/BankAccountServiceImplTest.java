package com.nttdata.bank_account_service.service;

import com.nttdata.bank_account_service.client.CreditClient;
import com.nttdata.bank_account_service.client.CustomerClient;
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

        Mono<BankAccountResponse> result = bankAccountService.create(request);

        StepVerifier.create(result)
                .expectErrorMatches(throwable -> throwable instanceof IllegalArgumentException
                        && throwable.getMessage().contains("Un cliente empresarial no puede tener cuentas de ahorro ni a plazo fijo"))
                .verify();

        verify(bankAccountRepository, never()).save(any(BankAccount.class));
    }

    @Test
    @DisplayName("create: Cliente BUSINESS puede abrir cuenta CURRENT")
    void create_businessCustomer_currentAccount_success() {
        BankAccountRequest request = new BankAccountRequest();
        request.setCustomerId("cust-business-01");
        request.setAccountType("CURRENT");
        request.setBalance(1500.0);

        BankAccount currentAccount = BankAccount.builder()
                .id("acc-biz-01")
                .customerId("cust-business-01")
                .accountType(AccountType.CURRENT)
                .balance(1500.0)
                .build();

        when(customerClient.getCustomerById("cust-business-01")).thenReturn(Mono.just(businessCustomer));
        when(bankAccountRepository.save(any(BankAccount.class))).thenReturn(Mono.just(currentAccount));

        Mono<BankAccountResponse> result = bankAccountService.create(request);

        StepVerifier.create(result)
                .expectNextMatches(res -> "acc-biz-01".equals(res.getId()))
                .verifyComplete();

        verify(bankAccountRepository, times(1)).save(any(BankAccount.class));
    }

    @Test
    @DisplayName("create: Cuenta VIP falla si el cliente no tiene tarjeta de credito")
    void create_vipAccount_withoutCreditCard_shouldFail() {
        BankAccountRequest request = new BankAccountRequest();
        request.setCustomerId("cust-personal-01");
        request.setAccountType("VIP");

        when(customerClient.getCustomerById("cust-personal-01")).thenReturn(Mono.just(personalCustomer));
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
        when(bankAccountRepository.save(any(BankAccount.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        Mono<BankAccountResponse> result = bankAccountService.update("acc-001", updateReq);

        StepVerifier.create(result)
                .expectNextMatches(res -> Double.valueOf(850.0).equals(res.getBalance())
                        && "CURRENT".equalsIgnoreCase(res.getAccountType()))
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