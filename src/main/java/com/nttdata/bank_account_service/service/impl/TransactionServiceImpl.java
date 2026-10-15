package com.nttdata.bank_account_service.service.impl;

import com.nttdata.bank_account_service.dto.TransactionRequest;
import com.nttdata.bank_account_service.dto.TransactionResponse;
import com.nttdata.bank_account_service.dto.TransferRequest;
import com.nttdata.bank_account_service.enums.AccountType;
import com.nttdata.bank_account_service.enums.TransactionType;
import com.nttdata.bank_account_service.mapper.TransactionMapper;
import com.nttdata.bank_account_service.model.BankAccount;
import com.nttdata.bank_account_service.model.Transaction;
import com.nttdata.bank_account_service.repository.BankAccountRepository;
import com.nttdata.bank_account_service.repository.TransactionRepository;
import com.nttdata.bank_account_service.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

/**
 * Implementación reactiva del servicio de movimientos de cuentas bancarias.
 * <p>
 * Además del CRUD completo de la entidad {@link Transaction}, implementa las reglas de
 * comisión y las restricciones por tipo de producto:
 * <ul>
 *     <li>El recuento de movimientos es <b>mensual</b>: solo se cuentan los movimientos
 *     registrados en el mes en curso.</li>
 *     <li>La <b>comisión de mantenimiento</b> de la cuenta corriente se cobra una única
 *     vez al mes, en el primer movimiento del mes.</li>
 *     <li>Al superar el <b>límite mensual de movimientos gratuitos</b> se añade la comisión
 *     por exceso ({@value #DEFAULT_FEE}).</li>
 *     <li>Las cuentas a <b>plazo fijo</b> solo admiten un depósito o retiro al mes y
 *     únicamente en el día habilitado de la cuenta.</li>
 * </ul>
 *
 * @author NTT Data
 * @see com.nttdata.bank_account_service.service.TransactionService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final BankAccountRepository accountRepository;

    /** Comisión por defecto que se aplica al superar el límite de movimientos del mes. */
    private static final Double DEFAULT_FEE = 3.50;

    /**
     * Registra un depósito o retiro aplicando las reglas de comisión y las restricciones
     * del tipo de producto bancario.
     *
     * @param accountId identificador de la cuenta que recibe el movimiento
     * @param request   tipo y monto del movimiento
     * @return {@code Mono} con el movimiento persistido
     */
    @Override
    public Mono<TransactionResponse> createTransaction(String accountId, TransactionRequest request) {
        TransactionType type = TransactionType.valueOf(request.getTransactionType().toUpperCase());
        Double amount = request.getAmount();

        if (amount == null || amount <= 0) {
            return Mono.error(new IllegalArgumentException("El monto del movimiento debe ser mayor que cero"));
        }

        // 1. Buscamos la cuenta
        return accountRepository.findById(accountId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("La cuenta no existe")))
                .flatMap(account -> executeMovement(account, accountId, request, type, amount));
    }

    /**
     * Encadena en orden las validaciones sin I/O y, solo si todo es correcto, las
     * consultas y la persistencia del movimiento:
     * <ol>
     *     <li>Tipo de movimiento admitido por el producto.</li>
     *     <li>Día del mes habilitado (cuentas a plazo fijo).</li>
     *     <li>Conteo de movimientos del mes en curso.</li>
     *     <li>Límite de un movimiento mensual (cuentas a plazo fijo).</li>
     *     <li>Cálculo de comisiones, validación de saldo y persistencia.</li>
     * </ol>
     * El conteo se envuelve en {@code Mono.defer} para que solo se consulte a MongoDB
     * cuando las validaciones previas han pasado.
     *
     * @param account   cuenta que recibe el movimiento
     * @param accountId identificador de la cuenta
     * @param request   datos del movimiento
     * @param type      tipo de movimiento
     * @param amount    importe del movimiento
     * @return {@code Mono} con el movimiento persistido o el error de negocio correspondiente
     */
    private Mono<TransactionResponse> executeMovement(BankAccount account, String accountId, TransactionRequest request,
                                                      TransactionType type, Double amount) {
        return validateTransactionType(account, type)
                .then(validateFixedTermDay(account))
                .then(Mono.defer(() -> countMonthlyMovements(accountId)))
                .flatMap(monthlyCount -> validateFixedTermMonthlyLimit(account, monthlyCount)
                        .then(Mono.defer(() -> applyMovement(account, accountId, request, type, amount, monthlyCount))));
    }

    /**
     * Devuelve el historial de movimientos de una cuenta.
     *
     * @param accountId identificador de la cuenta
     * @return flujo con los movimientos de la cuenta
     */
    @Override
    public Flux<TransactionResponse> getTransactionsByAccount(String accountId) {
        return transactionRepository.findByAccountId(accountId)
                .map(TransactionMapper::toResponse);
    }

    /**
     * Ejecuta una transferencia encadenando el retiro de la cuenta origen con el
     * depósito en la cuenta destino.
     *
     * @param request cuenta origen, cuenta destino e importe
     * @return {@code Mono} vacío al finalizar
     */
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

    /**
     * Devuelve todos los movimientos del sistema (FindAll de la entidad).
     *
     * @return flujo con todos los movimientos
     */
    @Override
    public Flux<TransactionResponse> findAll() {
        return transactionRepository.findAll().map(TransactionMapper::toResponse);
    }

    /**
     * Busca un movimiento por su identificador.
     *
     * @param id identificador del movimiento
     * @return {@code Mono} con el movimiento, o vacío si no existe
     */
    @Override
    public Mono<TransactionResponse> findById(String id) {
        return transactionRepository.findById(id).map(TransactionMapper::toResponse);
    }

    /**
     * Corrige el tipo o el importe de un movimiento ya registrado.
     * <p>
     * Es una operación administrativa de corrección del registro: no recalcula el saldo
     * de la cuenta, que solo se modifica mediante nuevos movimientos.
     *
     * @param id      identificador del movimiento
     * @param request nuevos tipo e importe
     * @return {@code Mono} con el movimiento corregido, o vacío si no existe
     */
    @Override
    public Mono<TransactionResponse> update(String id, TransactionRequest request) {
        log.info("Corrigiendo el movimiento con id: {}", id);

        TransactionType type = TransactionType.valueOf(request.getTransactionType().toUpperCase());
        Double amount = request.getAmount();

        if (amount == null || amount <= 0) {
            return Mono.error(new IllegalArgumentException("El monto del movimiento debe ser mayor que cero"));
        }

        return transactionRepository.findById(id)
                .flatMap(transaction -> {
                    transaction.setTransactionType(type);
                    transaction.setAmount(amount);
                    return transactionRepository.save(transaction);
                })
                .map(TransactionMapper::toResponse);
    }

    /**
     * Elimina un movimiento registrado.
     *
     * @param id identificador del movimiento
     * @return {@code Mono} vacío cuando termina
     */
    @Override
    public Mono<Void> delete(String id) {
        log.info("Eliminando el movimiento con id: {}", id);
        return transactionRepository.deleteById(id);
    }

    /**
     * Valida que el tipo de movimiento esté permitido por el producto bancario:
     * una cuenta a plazo fijo solo admite depósitos y retiros.
     *
     * @param account cuenta sobre la que se registra el movimiento
     * @param type    tipo de movimiento solicitado
     * @return {@code Mono} vacío si el tipo está permitido; error de negocio si no
     */
    private Mono<Void> validateTransactionType(BankAccount account, TransactionType type) {
        if (account.getAccountType() != AccountType.FIXED_TERM) {
            return Mono.empty();
        }
        if (type != TransactionType.DEPOSIT && type != TransactionType.WITHDRAWAL) {
            return Mono.error(new IllegalArgumentException(
                    "Una cuenta a plazo fijo solo admite movimientos de tipo DEPOSIT o WITHDRAWAL"));
        }
        return Mono.empty();
    }

    /**
     * Comprueba que la cuenta a plazo fijo solo se mueva en el día habilitado del mes.
     * <p>
     * Validación pura, sin acceso a base de datos, por lo que se ejecuta antes del
     * conteo mensual de movimientos.
     *
     * @param account cuenta sobre la que se registra el movimiento
     * @return {@code Mono} vacío si se respeta la regla; error de negocio si no
     */
    private Mono<Void> validateFixedTermDay(BankAccount account) {
        if (account.getAccountType() != AccountType.FIXED_TERM) {
            return Mono.empty();
        }

        int allowedDay = account.getFixedTermWithdrawalDay() != null
                ? account.getFixedTermWithdrawalDay()
                : 1;

        int today = LocalDate.now().getDayOfMonth();
        if (today != allowedDay) {
            return Mono.error(new IllegalArgumentException(
                    "Una cuenta a plazo fijo solo permite moverse el día " + allowedDay + " de cada mes"));
        }

        return Mono.empty();
    }

    /**
     * Comprueba que una cuenta a plazo fijo no haya hecho ya su movimiento del mes
     * en curso: solo permite un movimiento de retiro o depósito mensual.
     *
     * @param account      cuenta sobre la que se registra el movimiento
     * @param monthlyCount movimientos ya realizados en el mes en curso
     * @return {@code Mono} vacío si se respeta la regla; error de negocio si no
     */
    private Mono<Void> validateFixedTermMonthlyLimit(BankAccount account, long monthlyCount) {
        if (account.getAccountType() != AccountType.FIXED_TERM) {
            return Mono.empty();
        }
        if (monthlyCount >= 1) {
            return Mono.error(new IllegalArgumentException(
                    "Una cuenta a plazo fijo solo permite un movimiento de retiro o depósito al mes"));
        }
        return Mono.empty();
    }

    /**
     * Cuenta los movimientos de la cuenta registrados en el mes en curso.
     *
     * @param accountId identificador de la cuenta
     * @return número de movimientos de la cuenta en el mes actual
     */
    private Mono<Long> countMonthlyMovements(String accountId) {
        YearMonth currentMonth = YearMonth.now();
        LocalDateTime from = currentMonth.atDay(1).atStartOfDay();
        LocalDateTime to = currentMonth.plusMonths(1).atDay(1).atStartOfDay();
        return transactionRepository.countByAccountIdAndTransactionDateBetween(accountId, from, to);
    }

    /**
     * Calcula la comisión que corresponde pagar en este movimiento.
     *
     * @param account      cuenta que realiza el movimiento
     * @param monthlyCount movimientos ya realizados en el mes en curso
     * @return importe total de comisión a cobrar
     */
    private Double calculateFee(BankAccount account, long monthlyCount) {
        Double fee = 0.0;

        // Comisión de mantenimiento: una sola vez al mes (primer movimiento del mes)
        if (monthlyCount == 0 && account.getMaintenanceFee() != null && account.getMaintenanceFee() > 0) {
            fee += account.getMaintenanceFee();
            log.info("Cobrando comisión de mantenimiento de {} a la cuenta {} (primer movimiento del mes)",
                    account.getMaintenanceFee(), account.getId());
        }

        // Comisión por exceso sobre el límite mensual de movimientos gratuitos
        if (account.getMaxMovements() != null && monthlyCount >= account.getMaxMovements()) {
            fee += DEFAULT_FEE;
            log.info("Límite mensual de movimientos superado para la cuenta {}. Se cobrará comisión de {}",
                    account.getId(), DEFAULT_FEE);
        }

        return fee;
    }

    /**
     * Descuenta o acredita el importe del movimiento más la comisión, guarda la cuenta
     * con el nuevo saldo y registra el movimiento en el historial.
     *
     * @param account      cuenta que recibe el movimiento (se actualiza en memoria)
     * @param accountId    identificador de la cuenta
     * @param request      datos originales del movimiento
     * @param type         tipo de movimiento
     * @param amount       importe del movimiento
     * @param monthlyCount movimientos ya realizados en el mes en curso
     * @return {@code Mono} con el movimiento persistido
     */
    private Mono<TransactionResponse> applyMovement(BankAccount account, String accountId, TransactionRequest request,
                                                    TransactionType type, Double amount, long monthlyCount) {
        Double fee = calculateFee(account, monthlyCount);

        if (type == TransactionType.WITHDRAWAL || type == TransactionType.TRANSFER_OUT) {
            if (account.getBalance() < (amount + fee)) {
                return Mono.error(new IllegalArgumentException("Saldo insuficiente para el retiro/transferencia y comisiones"));
            }
            account.setBalance(account.getBalance() - amount - fee);
        } else if (type == TransactionType.DEPOSIT || type == TransactionType.TRANSFER_IN) {
            account.setBalance(account.getBalance() + amount - fee);
        }

        // 4. Guardamos la cuenta con el nuevo saldo, Y LUEGO guardamos el historial del movimiento
        return accountRepository.save(account)
                .then(Mono.defer(() -> transactionRepository.save(TransactionMapper.toEntity(accountId, request, fee))))
                .map(TransactionMapper::toResponse);
    }
}
