package com.fudn.bookingservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ReportResponse(LocalDate startDate, LocalDate endDate,
                             long totalBookings, long totalTickets, BigDecimal totalRevenue,
                             List<MovieRevenueResponse> revenueByMovie,
                             List<BookingResponse> bookings) {
}
