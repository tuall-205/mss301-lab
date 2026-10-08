package com.fudn.bookingservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateBookingRequest(
        @NotEmpty(message = "Booking must have at least 1 ticket")
        @Size(max = 8, message = "A booking can have at most 8 tickets")      // BR07
        List<@Valid BookingItemRequest> items) {
}
