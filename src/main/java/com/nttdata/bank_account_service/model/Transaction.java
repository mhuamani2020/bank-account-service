package com.nttdata.bank_account_service.model;

import com.nttdata.bank_account_service.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "transactions")
public class Transaction {
    @Id
    private String id;
    private String accountId;
    private TransactionType transactionType;
    private Double amount;
    private Double fee;
    private LocalDateTime transactionDate;
}
