package com.fudn.bookingservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record BookingItemRequest(
        @NotBlank(message = "Showtime id is required") String showtimeId,
        @NotBlank(message = "Seat code is required")
        @Pattern(regexp = "^[A-Z][1-9][0-9]?$", message = "Seat code must look like A1, E10 ...") String seatCode) {
}
