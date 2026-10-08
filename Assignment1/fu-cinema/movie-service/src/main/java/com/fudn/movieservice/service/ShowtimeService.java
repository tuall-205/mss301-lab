package com.fudn.movieservice.service;

import com.fudn.movieservice.dto.ShowtimeRequest;
import com.fudn.movieservice.dto.ShowtimeResponse;
import com.fudn.movieservice.exception.ApiException;
import com.fudn.movieservice.model.*;
import com.fudn.movieservice.repository.ShowtimeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ShowtimeService {

    /** Matches no _id -> used on create so that nothing is excluded from the overlap check */
    private static final String NO_EXCLUDE = "";

    private final ShowtimeRepository showtimeRepository;
    private final MovieService movieService;
    private final RoomService roomService;

    public ShowtimeResponse getById(String id) {
        Showtime s = find(id);
        return ShowtimeResponse.from(s, movieService.find(s.getMovieId()), roomService.find(s.getRoomId()));
    }

    public ShowtimeResponse create(ShowtimeRequest request) {
        Showtime showtime = new Showtime();
        showtime.setShowtimeStatus(ShowtimeStatus.SCHEDULED);
        return apply(showtime, request, NO_EXCLUDE);
    }

    public ShowtimeResponse update(String id, ShowtimeRequest request) {
        Showtime showtime = find(id);
        if (showtime.getShowtimeStatus() == ShowtimeStatus.CANCELLED) {
            throw ApiException.badRequest("Cannot update a cancelled showtime");
        }
        return apply(showtime, request, id);
    }

    private Showtime find(String id) {
        return showtimeRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Showtime not found with id: " + id));
    }

    private ShowtimeResponse apply(Showtime showtime, ShowtimeRequest request, String excludeId) {
        Movie movie = movieService.find(request.movieId());        // BR15
        CinemaRoom room = roomService.find(request.roomId());      // BR15

        // BR04
        if (movie.getMovieStatus() == MovieStatus.ENDED) {
            throw ApiException.badRequest("Movie '" + movie.getTitle() + "' has ENDED and cannot be scheduled");
        }
        if (room.getRoomStatus() != RoomStatus.ACTIVE) {
            throw ApiException.badRequest("Room '" + room.getRoomName() + "' is not ACTIVE");
        }
        if (!request.startTime().isAfter(LocalDateTime.now())) {
            throw ApiException.badRequest("Start time must be in the future");
        }
        LocalDateTime endTime = request.startTime().plusMinutes(movie.getDurationMinutes());

        // BR05
        long overlaps = showtimeRepository
                .countByRoomIdAndShowtimeStatusAndStartTimeLessThanAndEndTimeGreaterThanAndShowtimeIdNot(
                        room.getRoomId(), ShowtimeStatus.SCHEDULED, endTime, request.startTime(), excludeId);
        if (overlaps > 0) {
            throw ApiException.conflict("Room '" + room.getRoomName() + "' already has a showtime between "
                    + request.startTime() + " and " + endTime);
        }

        showtime.setMovieId(movie.getMovieId());
        showtime.setRoomId(room.getRoomId());
        showtime.setStartTime(request.startTime());
        showtime.setEndTime(endTime);
        showtime.setTicketPrice(request.ticketPrice());
        return ShowtimeResponse.from(showtimeRepository.save(showtime), movie, room);
    }
}
