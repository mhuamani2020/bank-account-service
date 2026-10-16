package com.nttdata.bank_account_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

/**
 * Configuración del {@link WebClient} compartido por los adaptadores de clientes
 * inter-microservicio.
 * <p>
 * Expone un {@code WebClient.Builder} como bean para que cada adaptador construya su
 * propio cliente conservando la misma configuración base.
 * <p>
 * Seguridad (Parte III): como este servicio es un <i>OAuth2 Resource Server</i>, las
 * llamadas REST que hace a {@code customer-service} / {@code credit-service} también deben
 * ir autenticadas. Para ello se propaga (<i>token relay</i>) el JWT de la petición entrante:
 * el {@link WebFilter} deja el {@link ServerWebExchange} en el contexto reactivo y el filtro
 * del {@link WebClient} copia la cabecera {@code Authorization} en la llamada saliente.
 *
 * @author NTT Data
 */
@Configuration
public class WebClientConfig {

    /** Clave del contexto reactivo donde se publica el intercambio HTTP entrante. */
    private static final String EXCHANGE_KEY = "serverWebExchange";

    /**
     * Publica el {@link ServerWebExchange} en el contexto reactivo para que las llamadas
     * salientes puedan reutilizar las cabeceras de la petición entrante.
     *
     * @return filtro web que propaga el contexto
     */
    @Bean
    public WebFilter exchangeContextWebFilter() {
        return (exchange, chain) -> chain.filter(exchange)
                .contextWrite(context -> context.put(EXCHANGE_KEY, exchange));
    }

    /**
     * Crea el builder que usan los adaptadores de clientes para construir sus WebClient,
     * con el filtro de propagación del token JWT.
     *
     * @return builder de WebClient
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder().filter(bearerTokenRelay());
    }

    /**
     * Filtro que reenvía la cabecera {@code Authorization} de la petición entrante a la
     * petición saliente. Si no hay contexto (p. ej. pruebas), no añade cabecera.
     *
     * @return filtro de intercambio para WebClient
     */
    private ExchangeFilterFunction bearerTokenRelay() {
        return (request, next) -> Mono.deferContextual(contextView -> {
            if (contextView.hasKey(EXCHANGE_KEY)) {
                ServerWebExchange exchange = contextView.get(EXCHANGE_KEY);
                String authorization = exchange.getRequest().getHeaders()
                        .getFirst(HttpHeaders.AUTHORIZATION);
                if (authorization != null && !authorization.isBlank()) {
                    ClientRequest relayed = ClientRequest.from(request)
                            .headers(headers -> headers.set(HttpHeaders.AUTHORIZATION, authorization))
                            .build();
                    return next.exchange(relayed);
                }
            }
            return next.exchange(request);
        });
    }
}
