package com.nttdata.bank_account_service.service.impl;

import com.nttdata.bank_account_service.client.CreditClient;
import com.nttdata.bank_account_service.client.CustomerClient;
import com.nttdata.bank_account_service.dto.AvailableBalanceResponse;
import com.nttdata.bank_account_service.dto.BankAccountRequest;
import com.nttdata.bank_account_service.dto.BankAccountResponse;
import com.nttdata.bank_account_service.dto.CustomerDTO;
import com.nttdata.bank_account_service.enums.AccountType;
import com.nttdata.bank_account_service.enums.CustomerType;
import com.nttdata.bank_account_service.mapper.BankAccountMapper;
import com.nttdata.bank_account_service.model.BankAccount;
import com.nttdata.bank_account_service.repository.BankAccountRepository;
import com.nttdata.bank_account_service.service.BankAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Implementación reactiva del servicio de cuentas bancarias.
 * <p>
 * Centraliza las operaciones CRUD de cuentas y aplica las <b>reglas de producto</b>
 * definidas por el negocio según el tipo de cuenta:
 * <ul>
 *     <li>{@code SAVINGS} (ahorro): libre de comisión de mantenimiento y con un límite
 *     máximo de movimientos mensuales.</li>
 *     <li>{@code CURRENT} (corriente): posee comisión de mantenimiento y no tiene límite
 *     de movimientos mensuales.</li>
 *     <li>{@code FIXED_TERM} (plazo fijo): libre de comisión de mantenimiento y solo
 *     admite un movimiento de retiro o depósito en un día específico del mes.</li>
 *     <li>{@code VIP} / {@code PYME} (Fase II): requieren que el cliente posea tarjeta
 *     de crédito.</li>
 * </ul>
 * Además, las cuentas de un cliente empresarial deben tener <b>uno o más titulares</b>
 * y pueden tener cero o más firmantes autorizados.
 * <p>
 * Todos los errores de negocio se emiten como {@link IllegalArgumentException} y son
 * traducidos a HTTP 400 por el manejador de excepciones estándar.
 *
 * @author NTT Data
 * @see com.nttdata.bank_account_service.service.BankAccountService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BankAccountServiceImpl implements BankAccountService {

    /** Día del mes que se asume para las cuentas a plazo fijo si no viene indicado. */
    private static final int DEFAULT_FIXED_TERM_WITHDRAWAL_DAY = 1;

    private final BankAccountRepository bankAccountRepository;
    private final CustomerClient customerClient;
    private final CreditClient creditClient;

    /**
     * Devuelve todas las cuentas bancarias del sistema.
     *
     * @return flujo con todas las cuentas mapeadas a DTO de respuesta
     */
    @Override
    public Flux<BankAccountResponse> findAll() {
        return bankAccountRepository.findAll().map(BankAccountMapper::toResponse);
    }

    /**
     * Busca una cuenta por su identificador.
     *
     * @param id identificador de la cuenta
     * @return {@code Mono} con la cuenta, o vacío si no existe
     */
    @Override
    public Mono<BankAccountResponse> findById(String id) {
        return bankAccountRepository.findById(id).map(BankAccountMapper::toResponse);
    }

    /**
     * Consulta el saldo disponible de una cuenta por su identificador.
     *
     * @param id identificador de la cuenta
     * @return {@code Mono} con el saldo disponible, o vacío si la cuenta no existe
     */
    @Override
    public Mono<AvailableBalanceResponse> getBalance(String id) {
        log.info("Consultando saldo disponible de la cuenta: {}", id);
        return bankAccountRepository.findById(id).map(account -> {
            AvailableBalanceResponse response = new AvailableBalanceResponse();
            response.setAccountId(account.getId());
            response.setCustomerId(account.getCustomerId());
            response.setAccountType(account.getAccountType() != null ? account.getAccountType().name() : null);
            response.setBalance(account.getBalance());
            return response;
        });
    }

    /**
     * Crea una cuenta bancaria validando primero que el cliente exista y que cumpla las
     * reglas de negocio de asignación de productos.
     * <p>
     * Para cuentas {@code VIP} y {@code PYME} (Fase II) consulta de forma <b>asíncrona</b>
     * a {@code credit-service} si el cliente posee tarjeta de crédito; esa llamada está
     * protegida por circuit breaker y degrada a HTTP 503 si el servicio no está disponible.
     *
     * @param request datos de la cuenta a crear
     * @return {@code Mono} con la cuenta creada
     */
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

    /**
     * Valida las reglas de asignación de productos (tipo de cliente vs tipo de cuenta y
     * un máximo de una cuenta de cada tipo por cliente personal) antes de persistir.
     *
     * @param customer cliente dueño de la cuenta
     * @param request  datos de la cuenta a crear
     * @return {@code Mono} con la cuenta persistida
     */
    private Mono<BankAccountResponse> validateAndSave(CustomerDTO customer, BankAccountRequest request) {
        CustomerType cType = CustomerType.valueOf(customer.getCustomerType().toUpperCase());
        AccountType aType = AccountType.valueOf(request.getAccountType().toUpperCase());

        if (cType == CustomerType.BUSINESS) {
            if (aType == AccountType.SAVINGS || aType == AccountType.FIXED_TERM) {
                return Mono.error(new IllegalArgumentException("Un cliente empresarial no puede tener cuentas de ahorro ni a plazo fijo"));
            }
            return saveAccount(cType, request);
        }

        if (cType == CustomerType.PERSONAL) {
            return bankAccountRepository.countByCustomerIdAndAccountType(customer.getId(), aType.name())
                    .flatMap(count -> {
                        if (count >= 1) {
                            return Mono.error(new IllegalArgumentException("Un cliente personal solo puede tener un máximo de una cuenta de tipo " + aType));
                        }
                        return saveAccount(cType, request);
                    });
        }

        return Mono.error(new IllegalArgumentException("Tipo de cliente desconocido"));
    }

    /**
     * Aplica las reglas de producto, persiste la cuenta y la devuelve como DTO.
     *
     * @param customerType tipo de cliente (personal o empresarial)
     * @param request      datos de la cuenta recibidos por la API
     * @return {@code Mono} con la cuenta persistida
     */
    private Mono<BankAccountResponse> saveAccount(CustomerType customerType, BankAccountRequest request) {
        return Mono.just(request)
                .map(BankAccountMapper::toEntity)
                .flatMap(account -> applyProductRules(account, customerType))
                .flatMap(bankAccountRepository::save)
                .map(BankAccountMapper::toResponse);
    }

    /**
     * Normaliza y valida la cuenta según las reglas de producto de la Parte I:
     * <ul>
     *     <li><b>Ahorro</b>: se fuerza comisión de mantenimiento a {@code 0} y es
     *     obligatorio indicar un límite máximo de movimientos mensuales.</li>
     *     <li><b>Corriente</b>: exige comisión de mantenimiento mayor que cero y se
     *     fuerza {@code maxMovements = null} (sin límite de movimientos mensuales).</li>
     *     <li><b>Plazo fijo</b>: se fuerza comisión de mantenimiento a {@code 0} y se
     *     fija el día del mes habilitado para su único movimiento (1 si no se indicó).</li>
     *     <li><b>Cuentas empresariales</b>: exige uno o más titulares; los firmantes
     *     autorizados son opcionales (se normaliza a lista vacía si no vienen).</li>
     * </ul>
     *
     * @param account      cuenta a normalizar
     * @param customerType tipo de cliente que poseerá la cuenta
     * @return {@code Mono} con la cuenta normalizada o un error de negocio ({@code 400})
     */
    private Mono<BankAccount> applyProductRules(BankAccount account, CustomerType customerType) {
        AccountType aType = account.getAccountType();

        if (aType == AccountType.SAVINGS) {
            // Ahorro: libre de comisión por mantenimiento y con límite mensual de movimientos
            account.setMaintenanceFee(0.0);
            if (account.getMaxMovements() == null || account.getMaxMovements() <= 0) {
                return Mono.error(new IllegalArgumentException(
                        "Una cuenta de ahorro debe indicar un límite máximo de movimientos mensuales (maxMovements > 0)"));
            }
        } else if (aType == AccountType.CURRENT) {
            // Cuenta corriente: posee comisión de mantenimiento y no tiene límite de movimientos
            if (account.getMaintenanceFee() == null || account.getMaintenanceFee() <= 0) {
                return Mono.error(new IllegalArgumentException(
                        "Una cuenta corriente debe indicar una comisión de mantenimiento mayor que cero (maintenanceFee > 0)"));
            }
            account.setMaxMovements(null);
            log.debug("Cuenta corriente registrada sin límite de movimientos mensuales (comisión de mantenimiento {})", account.getMaintenanceFee());
        } else if (aType == AccountType.FIXED_TERM) {
            // Plazo fijo: libre de comisión y un único movimiento en un día concreto del mes
            account.setMaintenanceFee(0.0);
            if (account.getFixedTermWithdrawalDay() == null
                    || account.getFixedTermWithdrawalDay() < 1
                    || account.getFixedTermWithdrawalDay() > 31) {
                account.setFixedTermWithdrawalDay(DEFAULT_FIXED_TERM_WITHDRAWAL_DAY);
            }
            log.debug("Cuenta a plazo fijo habilitada para mover únicamente el día {}", account.getFixedTermWithdrawalDay());
        }

        // Cuentas bancarias empresariales: uno o más titulares y cero o más firmantes
        if (customerType == CustomerType.BUSINESS) {
            if (account.getHolders() == null || account.getHolders().isEmpty()) {
                return Mono.error(new IllegalArgumentException(
                        "Una cuenta bancaria empresarial debe tener al menos un titular"));
            }
            if (account.getSignatories() == null) {
                account.setSignatories(List.of());
            }
        }

        return Mono.just(account);
    }

    /**
     * Reemplaza por completo una cuenta existente (semántica de {@code PUT}) revalidando
     * las reglas de producto contra el cliente propietario.
     *
     * @param id      identificador de la cuenta a actualizar
     * @param request nuevos datos de la cuenta
     * @return {@code Mono} con la cuenta actualizada, o vacío si el ID no existe
     */
    @Override
    public Mono<BankAccountResponse> update(String id, BankAccountRequest request) {
        log.info("Actualizando cuenta bancaria con id: {}", id);

        return bankAccountRepository.findById(id)
                .flatMap(existingAccount -> customerClient.getCustomerById(existingAccount.getCustomerId())
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("El cliente no existe")))
                        .flatMap(customer -> {
                            CustomerType cType = CustomerType.valueOf(customer.getCustomerType().toUpperCase());

                            existingAccount.setAccountType(AccountType.valueOf(request.getAccountType().toUpperCase()));
                            existingAccount.setBalance(request.getBalance());
                            existingAccount.setMaintenanceFee(request.getMaintenanceFee());
                            existingAccount.setMaxMovements(request.getMaxMovements());
                            existingAccount.setFixedTermWithdrawalDay(request.getFixedTermWithdrawalDay());
                            existingAccount.setHolders(request.getHolders());
                            existingAccount.setSignatories(request.getSignatories());

                            return applyProductRules(existingAccount, cType);
                        }))
                .flatMap(bankAccountRepository::save)
                .map(BankAccountMapper::toResponse);
    }

    /**
     * Elimina una cuenta bancaria por su identificador.
     *
     * @param id identificador de la cuenta
     * @return {@code Mono} vacío cuando la eliminación termina
     */
    @Override
    public Mono<Void> delete(String id) {
        log.info("Eliminando cuenta bancaria con id: {}", id);
        return bankAccountRepository.deleteById(id);
    }
}
