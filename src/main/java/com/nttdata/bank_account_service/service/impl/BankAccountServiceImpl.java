package com.nttdata.bank_account_service.service.impl;

import com.nttdata.bank_account_service.client.CreditClient;
import com.nttdata.bank_account_service.client.CustomerClient;
import com.nttdata.bank_account_service.dto.BankAccountRequest;
import com.nttdata.bank_account_service.dto.BankAccountResponse;
import com.nttdata.bank_account_service.dto.CustomerDTO;
import com.nttdata.bank_account_service.enums.AccountType;
import com.nttdata.bank_account_service.enums.CustomerType;
import com.nttdata.bank_account_service.mapper.BankAccountMapper;
import com.nttdata.bank_account_service.repository.BankAccountRepository;
import com.nttdata.bank_account_service.service.BankAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Log4j2
@Service
@RequiredArgsConstructor
public class BankAccountServiceImpl implements BankAccountService {

    private final BankAccountRepository bankAccountRepository;
    private final CustomerClient customerClient;
    private final CreditClient creditClient;

    @Value("${app.urls.customer-service}")
    private String customerServiceUrl;

    @Override
    public Flux<BankAccountResponse> findAll() {
        return bankAccountRepository.findAll().map(BankAccountMapper::toResponse);
    }

    @Override
    public Mono<BankAccountResponse> findById(String id) {
        return bankAccountRepository.findById(id).map(BankAccountMapper::toResponse);
    }

    @Override
    public Mono<BankAccountResponse> create(BankAccountRequest request) {
        log.info("Iniciando validación para crear cuenta tipo: {}", request.getAccountType());

        return customerClient.getCustomerById(request.getCustomerId())
                .switchIfEmpty(Mono.error(new IllegalArgumentException("El cliente no existe")))
                .flatMap(customer -> {
                    AccountType accountType = AccountType.valueOf(request.getAccountType().toUpperCase());

                    // Fase II: Validación asíncrona para VIP y PYME (Circuit Breaker)
                    if (accountType == AccountType.VIP || accountType == AccountType.PYME) {
                        return creditClient.customerHasCreditCard(request.getCustomerId())
                                .flatMap(hasCreditCard -> {
                                    if (!hasCreditCard) {
                                        return Mono.error(new IllegalArgumentException("Para abrir una cuenta " + accountType + " el cliente debe poseer una tarjeta de crédito."));
                                    }
                                    // Si tiene tarjeta, continuamos con el flujo estricto de negocio
                                    return validateAndSave(customer, request);
                                });
                    }

                    // Si NO es VIP ni PYME, pasa directo a tu validación de negocio
                    return validateAndSave(customer, request);
                });
    }

    private Mono<BankAccountResponse> validateAndSave(CustomerDTO customer, BankAccountRequest request) {
        CustomerType cType = CustomerType.valueOf(customer.getCustomerType().toUpperCase());
        AccountType aType = AccountType.valueOf(request.getAccountType().toUpperCase());

        if (cType == CustomerType.BUSINESS) {
            if (aType == AccountType.SAVINGS || aType == AccountType.FIXED_TERM) {
                return Mono.error(new IllegalArgumentException("Un cliente empresarial no puede tener cuentas de ahorro ni a plazo fijo"));
            }
            return saveAccount(request);
        }

        if (cType == CustomerType.PERSONAL) {
            return bankAccountRepository.countByCustomerIdAndAccountType(customer.getId(), aType.name())
                    .flatMap(count -> {
                        if (count >= 1) {
                            return Mono.error(new IllegalArgumentException("Un cliente personal solo puede tener un máximo de una cuenta de tipo " + aType));
                        }
                        return saveAccount(request);
                    });
        }

        return Mono.error(new IllegalArgumentException("Tipo de cliente desconocido"));
    }

    private Mono<BankAccountResponse> saveAccount(BankAccountRequest request) {
        return Mono.just(request)
                .map(BankAccountMapper::toEntity)
                .flatMap(bankAccountRepository::save)
                .map(BankAccountMapper::toResponse);
    }

    @Override
    public Mono<BankAccountResponse> update(String id, BankAccountRequest request) {
        return bankAccountRepository.findById(id)
                .flatMap(existingAccount -> {
                    existingAccount.setAccountType(AccountType.valueOf(request.getAccountType().toUpperCase()));
                    existingAccount.setBalance(request.getBalance());
                    existingAccount.setMaintenanceFee(request.getMaintenanceFee());
                    existingAccount.setMaxMovements(request.getMaxMovements());
                    return bankAccountRepository.save(existingAccount);
                })
                .map(BankAccountMapper::toResponse);
    }

    @Override
    public Mono<Void> delete(String id) {
        return bankAccountRepository.deleteById(id);
    }
}