package com.fudn.orderservice.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderRequest(
    Long id,
    @NotBlank(message = "SKU code cannot be blank")
    String skuCode,
    @NotNull(message = "Price cannot be null")
    @Positive(message = "Price must be a positive value")
    BigDecimal price,
    @NotNull(message = "Quantity cannot be null")
    @Positive(message = "Quantity must be a positive value")
    Integer quantity
) {
}
