package com.nttdata.bank_account_service.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guarda la invariante del estándar de resiliencia que provocaba el bug original:
 * sin {@code minimumNumberOfCalls} (o con un valor mayor que {@code slidingWindowSize})
 * el circuito <b>nunca</b> puede abrirse.
 */
class ResilienceConfigTest {

    private static final List<String> CIRCUIT_BREAKERS = List.of("customerServiceCB", "creditServiceCB");

    @Test
    @DisplayName("customerServiceCB y creditServiceCB pueden abrirse con la configuración declarada")
    void circuitBreakerInstancesCanOpen() {
        Map<String, Object> config = loadApplicationYaml();
        Map<String, Object> instances = mapAt(config, "resilience4j", "circuitbreaker", "instances");

        for (String instance : CIRCUIT_BREAKERS) {
            assertCircuitCanOpen(mapAt(instances, instance), instance);
        }
    }

    private void assertCircuitCanOpen(Map<String, Object> instanceConfig, String instance) {
        Integer minimumNumberOfCalls = (Integer) instanceConfig.get("minimumNumberOfCalls");
        Integer slidingWindowSize = (Integer) instanceConfig.get("slidingWindowSize");

        assertThat(minimumNumberOfCalls)
                .as("%s.minimumNumberOfCalls debe estar definido (el valor por defecto 100 impediría abrir el circuito)", instance)
                .isNotNull();
        assertThat(slidingWindowSize)
                .as("%s.slidingWindowSize debe estar definido", instance)
                .isNotNull();
        assertThat(minimumNumberOfCalls)
                .as("%s.minimumNumberOfCalls (%s) debe ser <= slidingWindowSize (%s)", instance, minimumNumberOfCalls, slidingWindowSize)
                .isLessThanOrEqualTo(slidingWindowSize);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapAt(Map<String, Object> root, String... path) {
        Map<String, Object> current = root;
        for (String key : path) {
            Object next = current.get(key);
            if (!(next instanceof Map)) {
                throw new AssertionError("Falta la sección de configuración: " + String.join(".", path));
            }
            current = (Map<String, Object>) next;
        }
        return current;
    }

    private static Map<String, Object> loadApplicationYaml() {
        try (InputStream input = new ClassPathResource("application.yml").getInputStream()) {
            return new Yaml().load(input);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo cargar application.yml para validar la configuración de resiliencia", ex);
        }
    }
}
