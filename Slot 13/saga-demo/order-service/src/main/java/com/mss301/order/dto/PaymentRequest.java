package com.mss301.order.dto;

public record PaymentRequest(Long orderId, String customerId, double amount) {
}
