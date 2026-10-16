package com.nttdata.bank_account_service.mapper;

import com.nttdata.bank_account_service.dto.DebitCardResponse;
import com.nttdata.bank_account_service.enums.DebitCardStatus;
import com.nttdata.bank_account_service.model.BankAccount;
import com.nttdata.bank_account_service.model.DebitCard;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Conversor estático entre la entidad {@link DebitCard} y su DTO de respuesta
 * {@link DebitCardResponse}. Se mantiene como clase de métodos estáticos porque la
 * transformación no tiene estado.
 *
 * @author NTT Data
 */
public class DebitCardMapper {

    /** Clase de utilidad: no se instancia. */
    private DebitCardMapper() {
    }

    /**
     * Construye la entidad {@link DebitCard} que se persistirá, derivando el cliente
     * titular de la cuenta asociada y generando el número de tarjeta.
     *
     * @param account cuenta bancaria a la que se asocia la tarjeta
     * @param status  estado inicial de la tarjeta
     * @return entidad {@link DebitCard} lista para guardarse
     */
    public static DebitCard toEntity(BankAccount account, DebitCardStatus status) {
        return DebitCard.builder()
                .accountId(account.getId())
                .customerId(account.getCustomerId())
                .cardNumber(generateCardNumber())
                .status(status)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * Convierte la entidad persistida en su DTO de respuesta, ajustando la fecha de
     * creación al formato OffsetDateTime que espera OpenAPI.
     *
     * @param card tarjeta leída de MongoDB
     * @return DTO de respuesta con los datos públicos de la tarjeta
     */
    public static DebitCardResponse toResponse(DebitCard card) {
        DebitCardResponse response = new DebitCardResponse();
        response.setId(card.getId());
        response.setAccountId(card.getAccountId());
        response.setCustomerId(card.getCustomerId());
        response.setCardNumber(card.getCardNumber());
        response.setStatus(card.getStatus() != null ? card.getStatus().name() : null);
        if (card.getCreatedAt() != null) {
            response.setCreatedAt(card.getCreatedAt().atOffset(ZoneOffset.UTC));
        }
        return response;
    }

    /**
     * Genera un número de tarjeta de débito de 16 dígitos (prefijo 4), de forma
     * aleatoria. No es un número real: es un identificador demostrativo del proyecto.
     *
     * @return número de tarjeta de 16 dígitos
     */
    private static String generateCardNumber() {
        long random = ThreadLocalRandom.current().nextLong(0L, 1_000_000_000_000_000L);
        return String.format("4%015d", random);
    }
}
