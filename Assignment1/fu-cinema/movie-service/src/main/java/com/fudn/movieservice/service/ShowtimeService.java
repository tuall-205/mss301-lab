package com.fudn.movieservice.service;

import com.fudn.movieservice.dto.ShowtimeRequest;
import com.fudn.movieservice.dto.ShowtimeResponse;
import com.fudn.movieservice.exception.ApiException;
import com.fudn.movieservice.model.*;
import com.fudn.movieservice.repository.MovieRepository;
import com.fudn.movieservice.repository.RoomRepository;
import com.fudn.movieservice.repository.ShowtimeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShowtimeService {

    /** Matches no _id -> used on create so that nothing is excluded from the overlap check */
    private static final String NO_EXCLUDE = "";

    private final ShowtimeRepository showtimeRepository;
    private final MovieRepository movieRepository;
    private final RoomRepository roomRepository;
    private final MovieService movieService;
    private final RoomService roomService;

    /** Filter by movieId and/or show date */
    public List<ShowtimeResponse> search(String movieId, LocalDate date) {
        List<Showtime> showtimes = (movieId == null || movieId.isBlank())
                ? showtimeRepository.findAllByOrderByStartTimeAsc()
                : showtimeRepository.findByMovieIdOrderByStartTimeAsc(movieId);
        List<Showtime> filtered = showtimes.stream()
                .filter(s -> date == null || s.getStartTime().toLocalDate().equals(date))
                .toList();
        return toResponses(filtered);
    }

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

    /** BR06: soft delete - the showtime is kept but marked CANCELLED */
    public void cancel(String id) {
        Showtime showtime = find(id);
        showtime.setShowtimeStatus(ShowtimeStatus.CANCELLED);
        showtimeRepository.save(showtime);
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

    /** Application-side join for lists: load the related movies and rooms with two findAllById queries */
    private List<ShowtimeResponse> toResponses(List<Showtime> showtimes) {
        Map<String, Movie> movies = movieRepository
                .findAllById(showtimes.stream().map(Showtime::getMovieId).distinct().toList())
                .stream().collect(Collectors.toMap(Movie::getMovieId, Function.identity()));
        Map<String, CinemaRoom> rooms = roomRepository
                .findAllById(showtimes.stream().map(Showtime::getRoomId).distinct().toList())
                .stream().collect(Collectors.toMap(CinemaRoom::getRoomId, Function.identity()));
        return showtimes.stream()
                .map(s -> ShowtimeResponse.from(s, movies.get(s.getMovieId()), rooms.get(s.getRoomId())))
                .toList();
    }
}
