package com.mss301.payment.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Wallet {
    @Id
    private String customerId;
    private double balance;
}
