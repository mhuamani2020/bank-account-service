package com.nttdata.bank_account_service.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Debe transformar IllegalArgumentException en HTTP 400 Bad Request")
    void handleIllegalArgumentException_shouldReturnBadRequest() {
        IllegalArgumentException ex = new IllegalArgumentException("Saldo insuficiente");

        Mono<ResponseEntity<Map<String, Object>>> responseMono = handler.handleIllegalArgumentException(ex);

        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
                    Map<String, Object> body = response.getBody();
                    assertNotNull(body);
                    assertEquals("Saldo insuficiente", body.get("message"));
                    assertEquals(400, body.get("status"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Debe transformar Exception no controlada en HTTP 500 Internal Server Error")
    void handleGenericException_shouldReturnInternalServerError() {
        Exception ex = new RuntimeException("Database timeout");

        Mono<ResponseEntity<Map<String, Object>>> responseMono = handler.handleGenericException(ex);

        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
                    Map<String, Object> body = response.getBody();
                    assertNotNull(body);
                    assertEquals("Ocurrió un error inesperado al procesar la solicitud", body.get("message"));
                    assertEquals(500, body.get("status"));
                })
                .verifyComplete();
    }
}
