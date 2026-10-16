package com.nttdata.bank_account_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de arranque del microservicio de cuentas bancarias (Pasivos).
 * <p>
 * Expone una API REST reactiva (WebFlux) con persistencia en MongoDB, registro en Eureka
 * y configuración externalizada en el Config Server.
 *
 * @author NTT Data
 */
@SpringBootApplication
public class BankAccountServiceApplication {

    /** Arranca el microservicio de cuentas bancarias. */
    public static void main(String[] args) {
        SpringApplication.run(BankAccountServiceApplication.class, args);
    }

}
