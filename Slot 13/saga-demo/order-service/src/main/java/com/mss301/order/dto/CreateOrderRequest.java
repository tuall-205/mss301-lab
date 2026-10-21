package com.mss301.order.dto;

public record CreateOrderRequest(String customerId, String productId, int quantity, double amount) {
}
