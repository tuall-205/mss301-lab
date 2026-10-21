package com.mss301.order.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter @Setter @NoArgsConstructor
public class SagaLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long orderId;
    private String step;      // VD: RESERVE_INVENTORY, PROCESS_PAYMENT, COMPENSATION
    private String result;    // SUCCESS / FAILED
    @Column(length = 1000)
    private String message;
    private LocalDateTime time;

    public SagaLog(Long orderId, String step, String result, String message) {
        this.orderId = orderId;
        this.step = step;
        this.result = result;
        this.message = message;
        this.time = LocalDateTime.now();
    }
}
