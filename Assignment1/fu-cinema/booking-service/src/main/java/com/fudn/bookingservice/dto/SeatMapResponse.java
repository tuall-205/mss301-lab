package com.fudn.bookingservice.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SeatMapResponse(String showtimeId, String movieTitle, String roomName, LocalDateTime startTime,
                              int seatRows, int seatsPerRow, int totalSeats, int availableSeats,
                              List<String> bookedSeats) {
}
