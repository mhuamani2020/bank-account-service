package com.nttdata.bank_account_service.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nttdata.bank_account_service.controller.ReportController;
import com.nttdata.bank_account_service.service.ReportService;
import io.reactivex.rxjava3.core.Flowable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;

/**
 * Prueba del <b>flujo de autenticación con JWT</b> (Parte III) sobre la seguridad real del
 * servicio ({@link SecurityConfig}, no la de pruebas).
 * <p>
 * Se firma un token con la misma clave simétrica que usa {@code auth-service} y se comprueba
 * de extremo a extremo: sin token → 401, con token válido → 200, con token firmado por otra
 * clave → 401 (el {@code ReactiveJwtDecoder} rechaza la firma).
 *
 * @author NTT Data
 */
@WebFluxTest(controllers = ReportController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.jwt.secret=bank-system-super-secret-key-for-jwt-hs256-2026-nttdata")
class SecurityFlowTest {

    /** Clave compartida con auth-service (misma que app.jwt.secret de la prueba). */
    private static final String SECRET = "bank-system-super-secret-key-for-jwt-hs256-2026-nttdata";

    private static final String ENDPOINT = "/reports/debit-card/c1/last-movements";

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ReportService reportService;

    @Test
    @DisplayName("Sin token JWT la petición es rechazada con 401")
    void requestWithoutTokenIsUnauthorized() {
        webTestClient.get().uri(ENDPOINT)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Con un JWT válido la petición es autorizada y llega al controlador")
    void requestWithValidTokenIsAuthorized() {
        when(reportService.getDebitCardLastMovements("c1")).thenReturn(Flowable.empty());

        webTestClient.get().uri(ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + signedToken(SECRET))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("Con un JWT firmado por otra clave la petición es rechazada con 401")
    void requestWithTokenSignedWithAnotherKeyIsUnauthorized() {
        webTestClient.get().uri(ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + signedToken("otra-clave-simetrica-distinta-para-hs256-000000"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    /**
     * Firma un JWT HS256 con la clave indicada, igual que hace {@code auth-service}.
     *
     * @param secret clave simétrica de firma
     * @return token JWT firmado
     */
    private String signedToken(String secret) {
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("bank-system")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .subject("admin")
                .claim("roles", List.of("ADMIN"))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
