package com.fudn.bookingservice.dto;

import com.fudn.bookingservice.model.Booking;
import com.fudn.bookingservice.model.BookingStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record BookingResponse(Long bookingId, LocalDateTime bookingDate, Long customerId,
                              BigDecimal totalPrice, BookingStatus bookingStatus,
                              List<BookingDetailResponse> details) {
    public static BookingResponse from(Booking b) {
        return new BookingResponse(b.getBookingId(), b.getBookingDate(), b.getCustomerId(),
                b.getTotalPrice(), b.getBookingStatus(),
                b.getDetails().stream().map(BookingDetailResponse::from).toList());
    }
}
