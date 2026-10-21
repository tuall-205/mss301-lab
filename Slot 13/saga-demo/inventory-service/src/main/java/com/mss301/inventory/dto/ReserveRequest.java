package com.mss301.inventory.dto;

public record ReserveRequest(Long orderId, String productId, int quantity) {
}
