package com.mss301.payment.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter @Setter @NoArgsConstructor
public class Payment {
    @Id
    private Long orderId;
    private String customerId;
    private double amount;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;
}
