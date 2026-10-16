package com.nttdata.bank_account_service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propiedades externalizadas con los nombres de los <i>topics</i> de Kafka que utiliza el
 * servicio (Parte I: "No deben tener configuraciones en el código").
 * <p>
 * Se leen de {@code app.kafka.topics.*} tanto del {@code application.yml} local como del
 * repositorio de configuración centralizada ({@code bank-config-repo}).
 *
 * @author NTT Data
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.kafka.topics")
public class KafkaTopicsProperties {

    /** Topic donde se publican los eventos de dominio de cada movimiento de cuenta. */
    private String accountTransactions;

    /** Topic donde se consumen los comandos de operación de monedero (Yanki). */
    private String walletOperations;

    /** Topic donde se publican los resultados de las operaciones de monedero. */
    private String walletOperationResults;
}
