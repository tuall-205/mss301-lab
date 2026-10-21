package com.mss301.payment.dto;

public record PaymentRequest(Long orderId, String customerId, double amount) {
}
