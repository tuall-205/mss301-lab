package com.fudn.movieservice.dto;

import com.fudn.movieservice.model.CinemaRoom;
import com.fudn.movieservice.model.Movie;
import com.fudn.movieservice.model.Showtime;
import com.fudn.movieservice.model.ShowtimeStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Joins showtimes + movies + cinema_rooms so booking-service has everything it needs. */
public record ShowtimeResponse(String showtimeId, String movieId, String movieTitle,
                               String roomId, String roomName, int seatRows, int seatsPerRow,
                               LocalDateTime startTime, LocalDateTime endTime,
                               BigDecimal ticketPrice, ShowtimeStatus showtimeStatus) {
    public static ShowtimeResponse from(Showtime s, Movie movie, CinemaRoom room) {
        return new ShowtimeResponse(s.getShowtimeId(),
                movie.getMovieId(), movie.getTitle(),
                room.getRoomId(), room.getRoomName(), room.getSeatRows(), room.getSeatsPerRow(),
                s.getStartTime(), s.getEndTime(), s.getTicketPrice(), s.getShowtimeStatus());
    }
}
