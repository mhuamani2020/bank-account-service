package com.nttdata.bank_account_service.model;

import com.nttdata.bank_account_service.enums.AccountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * Entidad de negocio que representa una cuenta bancaria (Pasivo) persistida en MongoDB.
 * <p>
 * Colección propia ({@code bank_accounts}): siguiendo el patrón <b>database per service</b>
 * ningún otro microservicio accede a esta colección.
 * <p>
 * Reglas de producto por {@link AccountType}:
 * <ul>
 *     <li>{@code SAVINGS}: {@code maintenanceFee} = 0 y {@code maxMovements} obligatorio
 *     (límite máximo de movimientos <b>mensuales</b>).</li>
 *     <li>{@code CURRENT}: {@code maintenanceFee} &gt; 0 y {@code maxMovements} nulo
 *     (sin límite de movimientos mensuales).</li>
 *     <li>{@code FIXED_TERM}: {@code maintenanceFee} = 0 y solo un movimiento de retiro
 *     o depósito en el día {@code fixedTermWithdrawalDay} de cada mes.</li>
 * </ul>
 * Las cuentas empresariales admiten uno o más {@code holders} (titulares) y cero o más
 * {@code signatories} (firmantes autorizados).
 *
 * @author NTT Data
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "bank_accounts")
public class BankAccount {
    /** Identificador autogenerado de la cuenta. */
    @Id
    private String id;

    /** Identificador lógico del cliente dueño de la cuenta (customer-service). */
    private String customerId;

    /** Tipo de producto: SAVINGS, CURRENT, FIXED_TERM, VIP, PYME. */
    private AccountType accountType;

    /** Saldo disponible actual de la cuenta. */
    private Double balance;

    /** Comisión de mantenimiento: 0 en ahorro/plazo fijo, &gt; 0 en cuenta corriente. */
    private Double maintenanceFee;

    /** Movimientos mensuales libres de comisión; {@code null} significa "sin límite". */
    private Integer maxMovements;

    /** Día (1-31) de cada mes en que una cuenta a plazo fijo admite su único movimiento. */
    private Integer fixedTermWithdrawalDay;

    /** Titulares de la cuenta; obligatorio al menos uno en cuentas empresariales. */
    private List<String> holders;

    /** Firmantes autorizados; puede ser una lista vacía (cero firmantes). */
    private List<String> signatories;
}
