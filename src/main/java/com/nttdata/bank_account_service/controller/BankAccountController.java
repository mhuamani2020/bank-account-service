package com.nttdata.bank_account_service.controller;

import com.nttdata.bank_account_service.api.BankAccountsApi;
import com.nttdata.bank_account_service.dto.AvailableBalanceResponse;
import com.nttdata.bank_account_service.dto.BankAccountRequest;
import com.nttdata.bank_account_service.dto.BankAccountResponse;
import com.nttdata.bank_account_service.dto.TransactionRequest;
import com.nttdata.bank_account_service.dto.TransactionResponse;
import com.nttdata.bank_account_service.dto.TransferRequest;
import com.nttdata.bank_account_service.service.BankAccountService;
import com.nttdata.bank_account_service.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Controlador REST de cuentas bancarias y sus movimientos.
 * <p>
 * Implementa la interfaz {@link BankAccountsApi} generada a partir del contrato
 * OpenAPI ({@code src/main/resources/openapi.yaml}), de modo que la documentación
 * y la implementación no pueden divergir.
 * <p>
 * El controlador solo traduce HTTP &lt;-&gt; servicio: toda la regla de negocio vive en
 * la capa de servicio y los errores de negocio se devuelven como HTTP 400 mediante el
 * manejador de excepciones estándar.
 *
 * @author NTT Data
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class BankAccountController implements BankAccountsApi {

    private final BankAccountService bankAccountService;
    private final TransactionService transactionService; // 1. Inyectamos el servicio

    /**
     * {@code GET /bank-accounts} — lista todas las cuentas.
     *
     * @param exchange contexto de la petición
     * @return respuesta con el flujo de cuentas
     */
    @Override
    public Mono<ResponseEntity<Flux<BankAccountResponse>>> findAll(ServerWebExchange exchange) {
        log.info("Fetching all bank accounts");
        return Mono.just(ResponseEntity.ok(bankAccountService.findAll()));
    }

    /**
     * {@code POST /bank-accounts} — crea una cuenta bancaria.
     *
     * @param bankAccountRequest datos de la cuenta
     * @param exchange           contexto de la petición
     * @return respuesta 201 con la cuenta creada
     */
    @Override
    public Mono<ResponseEntity<BankAccountResponse>> create(
            Mono<BankAccountRequest> bankAccountRequest, ServerWebExchange exchange) {
        log.info("Creating new bank account");
        return bankAccountRequest
                .flatMap(bankAccountService::create)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
    }

    /**
     * {@code GET /bank-accounts/{id}} — consulta una cuenta por su identificador.
     *
     * @param id       identificador de la cuenta
     * @param exchange contexto de la petición
     * @return respuesta 200 con la cuenta, o 404 si no existe
     */
    @Override
    public Mono<ResponseEntity<BankAccountResponse>> findById(String id, ServerWebExchange exchange) {
        log.info("Fetching bank account by id: {}", id);
        return bankAccountService.findById(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * {@code GET /bank-accounts/{id}/balance} — consulta el saldo disponible de una
     * cuenta bancaria (Parte I: "consultar los saldos disponibles en sus productos").
     *
     * @param id       identificador de la cuenta
     * @param exchange contexto de la petición
     * @return respuesta 200 con el saldo disponible, o 404 si la cuenta no existe
     */
    @Override
    public Mono<ResponseEntity<AvailableBalanceResponse>> getAvailableBalance(String id, ServerWebExchange exchange) {
        log.info("Consultando saldo disponible de la cuenta: {}", id);
        return bankAccountService.getBalance(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * {@code PUT /bank-accounts/{id}} — reemplaza una cuenta existente.
     *
     * @param id                identificador de la cuenta
     * @param bankAccountRequest nuevos datos de la cuenta
     * @param exchange          contexto de la petición
     * @return respuesta 200 con la cuenta actualizada, o 404 si no existe
     */
    @Override
    public Mono<ResponseEntity<BankAccountResponse>> update(
            String id, Mono<BankAccountRequest> bankAccountRequest, ServerWebExchange exchange) {
        log.info("Updating bank account by id: {}", id);
        return bankAccountRequest
                .flatMap(request -> bankAccountService.update(id, request))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * {@code DELETE /bank-accounts/{id}} — elimina una cuenta.
     *
     * @param id       identificador de la cuenta
     * @param exchange contexto de la petición
     * @return respuesta 204 sin contenido
     */
    @Override
    public Mono<ResponseEntity<Void>> delete(String id, ServerWebExchange exchange) {
        log.info("Deleting bank account by id: {}", id);
        return bankAccountService.delete(id)
                .then(Mono.just(new ResponseEntity<Void>(HttpStatus.NO_CONTENT)));
    }

    /**
     * {@code POST /bank-accounts/{accountId}/transactions} — registra un depósito o retiro.
     *
     * @param accountId        identificador de la cuenta
     * @param transactionRequest tipo e importe del movimiento
     * @param exchange         contexto de la petición
     * @return respuesta 201 con el movimiento registrado
     */
    @Override
    public Mono<ResponseEntity<TransactionResponse>> createTransaction(
            String accountId, Mono<TransactionRequest> transactionRequest, ServerWebExchange exchange) {
        log.info("Creando transacción para la cuenta: {}", accountId);
        return transactionRequest
                .flatMap(request -> transactionService.createTransaction(accountId, request))
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
    }

    /**
     * {@code GET /bank-accounts/{accountId}/transactions} — historial de movimientos de una cuenta.
     *
     * @param accountId identificador de la cuenta
     * @param exchange  contexto de la petición
     * @return respuesta 200 con los movimientos de la cuenta
     */
    @Override
    public Mono<ResponseEntity<Flux<TransactionResponse>>> getTransactionsByAccount(
            String accountId, ServerWebExchange exchange) {
        log.info("Obteniendo historial de transacciones para la cuenta: {}", accountId);
        Flux<TransactionResponse> transactions = transactionService.getTransactionsByAccount(accountId);
        return Mono.just(ResponseEntity.ok(transactions));
    }

    /**
     * {@code POST /bank-accounts/transfers} — transfiere fondos entre dos cuentas.
     *
     * @param transferRequest cuenta origen, destino e importe
     * @param exchange        contexto de la petición
     * @return respuesta 201 si la transferencia se completó
     */
    @Override
    public Mono<ResponseEntity<Void>> createTransfer(
            Mono<TransferRequest> transferRequest, ServerWebExchange exchange) {
        log.info("Procesando solicitud de transferencia");
        return transferRequest
                .flatMap(transactionService::createTransfer)
                .thenReturn(ResponseEntity.status(HttpStatus.CREATED).build());
    }

    /**
     * {@code GET /bank-accounts/transactions} — lista todos los movimientos (FindAll).
     *
     * @param exchange contexto de la petición
     * @return respuesta 200 con todos los movimientos
     */
    @Override
    public Mono<ResponseEntity<Flux<TransactionResponse>>> findAllTransactions(ServerWebExchange exchange) {
        log.info("Fetching all transactions");
        return Mono.just(ResponseEntity.ok(transactionService.findAll()));
    }

    /**
     * {@code GET /bank-accounts/transactions/{transactionId}} — consulta un movimiento.
     *
     * @param transactionId identificador del movimiento
     * @param exchange      contexto de la petición
     * @return respuesta 200 con el movimiento, o 404 si no existe
     */
    @Override
    public Mono<ResponseEntity<TransactionResponse>> findTransactionById(
            String transactionId, ServerWebExchange exchange) {
        log.info("Fetching transaction by id: {}", transactionId);
        return transactionService.findById(transactionId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * {@code PUT /bank-accounts/transactions/{transactionId}} — corrige un movimiento.
     *
     * @param transactionId    identificador del movimiento
     * @param transactionRequest nuevos tipo e importe
     * @param exchange         contexto de la petición
     * @return respuesta 200 con el movimiento corregido, o 404 si no existe
     */
    @Override
    public Mono<ResponseEntity<TransactionResponse>> updateTransaction(
            String transactionId, Mono<TransactionRequest> transactionRequest, ServerWebExchange exchange) {
        log.info("Updating transaction by id: {}", transactionId);
        return transactionRequest
                .flatMap(request -> transactionService.update(transactionId, request))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * {@code DELETE /bank-accounts/transactions/{transactionId}} — elimina un movimiento.
     *
     * @param transactionId identificador del movimiento
     * @param exchange      contexto de la petición
     * @return respuesta 204 sin contenido
     */
    @Override
    public Mono<ResponseEntity<Void>> deleteTransaction(String transactionId, ServerWebExchange exchange) {
        log.info("Deleting transaction by id: {}", transactionId);
        return transactionService.delete(transactionId)
                .then(Mono.just(new ResponseEntity<Void>(HttpStatus.NO_CONTENT)));
    }
}
