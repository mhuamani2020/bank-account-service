package com.nttdata.bank_account_service.controller;

import com.nttdata.bank_account_service.api.DebitCardsApi;
import com.nttdata.bank_account_service.dto.DebitCardPaymentRequest;
import com.nttdata.bank_account_service.dto.DebitCardPaymentResponse;
import com.nttdata.bank_account_service.dto.DebitCardRequest;
import com.nttdata.bank_account_service.dto.DebitCardResponse;
import com.nttdata.bank_account_service.dto.TransactionResponse;
import com.nttdata.bank_account_service.service.DebitCardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Controlador REST de las tarjetas de débito (Parte III).
 * <p>
 * Implementa la interfaz {@link DebitCardsApi} generada a partir del contrato OpenAPI
 * ({@code src/main/resources/openapi.yaml}); así el contrato y la implementación no pueden
 * divergir (<i>contract-first</i>).
 * <p>
 * La reactividad de esta <b>nueva funcionalidad</b> se implementa con <b>RxJava 3</b> en la
 * capa de servicio ({@link DebitCardService} devuelve {@code Single}/{@code Maybe}/
 * {@code Flowable}/{@code Completable}); aquí, en el borde HTTP, esos tipos se adaptan a
 * Reactor (que es el tipo que declara la interfaz generada) mediante {@code
 * Mono.from(...toFlowable())} y {@code Flux.from(...)}.
 * <p>
 * El controlador solo traduce HTTP &lt;-&gt; servicio; las reglas de negocio viven en
 * {@link DebitCardService} y los errores de negocio se devuelven como HTTP 400 mediante
 * el manejador de excepciones estándar.
 *
 * @author NTT Data
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class DebitCardController implements DebitCardsApi {

    private final DebitCardService debitCardService;

    /**
     * {@code GET /debit-cards} — lista todas las tarjetas de débito.
     *
     * @param exchange contexto de la petición
     * @return respuesta 200 con el flujo de tarjetas
     */
    @Override
    public Mono<ResponseEntity<Flux<DebitCardResponse>>> findAllDebitCards(ServerWebExchange exchange) {
        log.info("Fetching all debit cards");
        return Mono.just(ResponseEntity.ok(Flux.from(debitCardService.findAll())));
    }

    /**
     * {@code POST /debit-cards} — emite una tarjeta de débito para una cuenta.
     *
     * @param debitCardRequest cuenta a asociar y estado inicial opcional
     * @param exchange         contexto de la petición
     * @return respuesta 201 con la tarjeta creada
     */
    @Override
    public Mono<ResponseEntity<DebitCardResponse>> createDebitCard(Mono<DebitCardRequest> debitCardRequest,
                                                                   ServerWebExchange exchange) {
        log.info("Creating debit card");
        return debitCardRequest
                .flatMap(request -> Mono.from(debitCardService.create(request).toFlowable()))
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
    }

    /**
     * {@code GET /debit-cards/{id}} — consulta una tarjeta de débito por su id.
     *
     * @param id       identificador de la tarjeta
     * @param exchange contexto de la petición
     * @return respuesta 200 con la tarjeta, o 404 si no existe
     */
    @Override
    public Mono<ResponseEntity<DebitCardResponse>> findDebitCardById(String id, ServerWebExchange exchange) {
        log.info("Fetching debit card by id: {}", id);
        return Mono.from(debitCardService.findById(id).toFlowable())
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * {@code PUT /debit-cards/{id}} — actualiza una tarjeta de débito (por ejemplo,
     * bloquearla o activarla).
     *
     * @param id               identificador de la tarjeta
     * @param debitCardRequest nuevos datos de la tarjeta
     * @param exchange         contexto de la petición
     * @return respuesta 200 con la tarjeta actualizada, o 404 si no existe
     */
    @Override
    public Mono<ResponseEntity<DebitCardResponse>> updateDebitCard(String id, Mono<DebitCardRequest> debitCardRequest,
                                                                   ServerWebExchange exchange) {
        log.info("Updating debit card by id: {}", id);
        return debitCardRequest
                .flatMap(request -> Mono.from(debitCardService.update(id, request).toFlowable()))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * {@code DELETE /debit-cards/{id}} — elimina una tarjeta de débito.
     *
     * @param id       identificador de la tarjeta
     * @param exchange contexto de la petición
     * @return respuesta 204 sin contenido
     */
    @Override
    public Mono<ResponseEntity<Void>> deleteDebitCard(String id, ServerWebExchange exchange) {
        log.info("Deleting debit card by id: {}", id);
        return Mono.from(debitCardService.delete(id).toFlowable())
                .then(Mono.just(new ResponseEntity<Void>(HttpStatus.NO_CONTENT)));
    }

    /**
     * {@code GET /debit-cards/customer/{customerId}} — tarjetas de débito de un cliente.
     *
     * @param customerId identificador del cliente
     * @param exchange   contexto de la petición
     * @return respuesta 200 con las tarjetas del cliente
     */
    @Override
    public Mono<ResponseEntity<Flux<DebitCardResponse>>> findDebitCardsByCustomer(String customerId,
                                                                                  ServerWebExchange exchange) {
        log.info("Fetching debit cards of customer: {}", customerId);
        return Mono.just(ResponseEntity.ok(Flux.from(debitCardService.findByCustomer(customerId))));
    }

    /**
     * {@code POST /debit-cards/{id}/payments} — realiza un pago con la tarjeta,
     * debitando la cuenta asociada.
     *
     * @param id                      identificador de la tarjeta
     * @param debitCardPaymentRequest importe y descripción del pago
     * @param exchange                contexto de la petición
     * @return respuesta 201 con el resultado del pago
     */
    @Override
    public Mono<ResponseEntity<DebitCardPaymentResponse>> payWithDebitCard(
            String id, Mono<DebitCardPaymentRequest> debitCardPaymentRequest, ServerWebExchange exchange) {
        log.info("Processing debit card payment for card: {}", id);
        return debitCardPaymentRequest
                .flatMap(request -> Mono.from(debitCardService.pay(id, request).toFlowable()))
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
    }

    /**
     * {@code GET /debit-cards/{id}/movements} — movimientos (pagos) de la tarjeta.
     *
     * @param id       identificador de la tarjeta
     * @param exchange contexto de la petición
     * @return respuesta 200 con los movimientos de la tarjeta
     */
    @Override
    public Mono<ResponseEntity<Flux<TransactionResponse>>> getDebitCardMovements(
            String id, ServerWebExchange exchange) {
        log.info("Fetching movements of debit card: {}", id);
        return Mono.just(ResponseEntity.ok(Flux.from(debitCardService.getMovements(id))));
    }
}
