package com.nttdata.bank_account_service.config;

import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * Configuración de Kafka del productor de eventos (Parte III).
 * <p>
 * Se declara un {@link KafkaTemplate} tipado a {@code <String, String>} porque los eventos
 * viajan como JSON serializado en texto; de esta forma los contratos entre servicios no
 * dependen de las clases Java de cada repositorio ni de las cabeceras de tipo de Spring
 * Kafka, lo que hace robusta la comunicación entre microservicios independientes.
 * <p>
 * La configuración de conexión ({@code spring.kafka.*}) se mantiene externalizada en los
 * ficheros {@code application.yml} y {@code bank-config-repo}, nunca en el código.
 *
 * @author NTT Data
 */
@Configuration
public class KafkaConfig {

    /**
     * Fábrica de productores que envía claves y valores como {@code String}.
     *
     * @param kafkaProperties propiedades de Kafka externalizadas
     * @param sslBundles      bundles SSL gestionados por Spring Boot (vacío por defecto)
     * @return fábrica de productores de texto
     */
    @Bean
    public ProducerFactory<String, String> stringProducerFactory(KafkaProperties kafkaProperties,
                                                                 SslBundles sslBundles) {
        return new DefaultKafkaProducerFactory<>(kafkaProperties.buildProducerProperties(sslBundles));
    }

    /**
     * Plantilla de envío de eventos a Kafka.
     *
     * @param producerFactory fábrica de productores de texto
     * @return plantilla tipada para publicar eventos JSON
     */
    @Bean
    public KafkaTemplate<String, String> stringKafkaTemplate(ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
