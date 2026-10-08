package com.fudn.movieservice.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShowtimeRequest(
        @NotBlank(message = "Movie id is required") String movieId,
        @NotBlank(message = "Room id is required") String roomId,
        @NotNull(message = "Start time is required")
        @Future(message = "Start time must be in the future") LocalDateTime startTime,
        @NotNull(message = "Ticket price is required")
        @DecimalMin(value = "10000", message = "Ticket price must be at least 10,000")
        @DecimalMax(value = "1000000", message = "Ticket price must not exceed 1,000,000") BigDecimal ticketPrice) {
}
