package com.nttdata.bank_account_service.model;

import com.nttdata.bank_account_service.enums.DebitCardStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Entidad de negocio que representa una tarjeta de débito asociada a una cuenta
 * bancaria (Parte III: "Los clientes ahora pueden tener tarjetas de débito asociado
 * a sus cuentas bancarias y hacer pagos con ellas").
 * <p>
 * Colección propia ({@code debit_cards}), en cumplimiento del patrón
 * <b>database per service</b>: ningún otro microservicio accede a esta colección.
 * <p>
 * Cada tarjeta apunta a una única cuenta ({@code accountId}); el {@code customerId}
 * se deriva del titular de esa cuenta. Los pagos realizados con la tarjeta se
 * registran como movimientos ({@link Transaction}) de tipo {@code DEBIT_CARD_PAYMENT}
 * referenciando el identificador de la tarjeta.
 *
 * @author NTT Data
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "debit_cards")
public class DebitCard {

    /** Identificador autogenerado de la tarjeta. */
    @Id
    private String id;

    /** Cuenta bancaria asociada a la tarjeta de débito. */
    private String accountId;

    /** Cliente titular de la cuenta asociada. */
    private String customerId;

    /** Número de tarjeta de débito generado. */
    private String cardNumber;

    /** Estado actual de la tarjeta: ACTIVE o BLOCKED. */
    private DebitCardStatus status;

    /** Fecha y hora de creación de la tarjeta. */
    private LocalDateTime createdAt;
}
