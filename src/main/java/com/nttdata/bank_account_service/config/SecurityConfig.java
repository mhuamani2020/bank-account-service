package com.nttdata.bank_account_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Configuración de seguridad reactiva de {@code bank-account-service} como
 * <b>OAuth2 Resource Server</b> (Parte III: flujo de autenticación/autorización con JWT).
 * <p>
 * <b>Autenticación</b>: exige un JWT válido (emitido por {@code auth-service}) en todos los
 * endpoints de negocio; solo quedan públicos los de actuator y documentación.
 * <p>
 * <b>Autorización</b>: los roles que viajan en el claim {@code roles} del token se resuelven
 * como autoridades {@code ROLE_*} en el contexto de seguridad, de modo que las decisiones de
 * acceso se toman a partir del propio JWT.
 * <p>
 * La clave simétrica se externaliza en {@code app.jwt.secret} (Config Server) y es la misma
 * con la que {@code auth-service} firma los tokens.
 *
 * @author NTT Data
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /**
     * Define la cadena de filtros de seguridad reactiva.
     *
     * @param http configuración de seguridad reactiva
     * @return cadena de filtros de seguridad
     */
    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/actuator/**", "/v3/api-docs/**",
                                "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwtSpec -> jwtSpec
                        .jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    /**
     * Decodificador reactivo de JWT firmados con HMAC-SHA256.
     *
     * @param secret clave simétrica externalizada
     * @return decodificador reactivo de JWT
     */
    @Bean
    public ReactiveJwtDecoder jwtDecoder(@Value("${app.jwt.secret}") String secret) {
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusReactiveJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * Resuelve las autoridades del usuario a partir del claim {@code roles} del JWT,
     * exponiéndolas como {@code ROLE_*} para la autorización.
     *
     * @return adaptador reactivo que traduce el JWT a una autenticación con roles
     */
    private ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return new ReactiveJwtAuthenticationConverterAdapter(jwtConverter);
    }
}
