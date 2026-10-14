package com.nttdata.bank_account_service.model;

import com.nttdata.bank_account_service.enums.AccountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "bank_accounts")
public class BankAccount {
    @Id
    private String id;
    private String customerId; // Enlace lógico con customer-service
    private AccountType accountType; // SAVINGS, CURRENT, FIXED_TERM
    private Double balance;
    private Double maintenanceFee;
    private Integer maxMovements;
}
