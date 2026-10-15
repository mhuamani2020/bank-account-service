package com.nttdata.bank_account_service.client;

import com.nttdata.bank_account_service.dto.CustomerDTO;
import reactor.core.publisher.Mono;

/**
 * Cliente reactivo hacia {@code customer-service}.
 * <p>
 * Permite validar que el titular de una cuenta existe antes de crearla sin romper el
 * patrón <i>database per service</i>.
 *
 * @author NTT Data
 */
public interface CustomerClient {

    /**
     * Obtiene la ficha de un cliente por su identificador.
     *
     * @param customerId identificador del cliente
     * @return {@code Mono} con el cliente, vacío si no existe o si el servicio no está disponible
     */
    Mono<CustomerDTO> getCustomerById(String customerId);
}
