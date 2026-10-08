package com.fudn.bookingservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Contract copy of movie-service's response. Ids are Strings (Mongo ObjectId); status stays a String to avoid sharing enums. */
public record ShowtimeResponse(String showtimeId, String movieId, String movieTitle,
                               String roomId, String roomName, int seatRows, int seatsPerRow,
                               LocalDateTime startTime, LocalDateTime endTime,
                               BigDecimal ticketPrice, String showtimeStatus) {
}
