package com.mss301.order.dto;

public record ReserveRequest(Long orderId, String productId, int quantity) {
}
