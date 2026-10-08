package com.fudn.bookingservice.service;

import com.fudn.bookingservice.client.MovieClient;
import com.fudn.bookingservice.dto.*;
import com.fudn.bookingservice.exception.ApiException;
import com.fudn.bookingservice.model.Booking;
import com.fudn.bookingservice.model.BookingDetail;
import com.fudn.bookingservice.model.BookingStatus;
import com.fudn.bookingservice.repository.BookingDetailRepository;
import com.fudn.bookingservice.repository.BookingRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingService {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final long CANCEL_BEFORE_HOURS = 2;

    private final BookingRepository bookingRepository;
    private final BookingDetailRepository bookingDetailRepository;
    private final MovieClient movieClient;

    // ======================= F7: CREATE BOOKING =======================

    @Transactional
    public BookingResponse create(Long customerId, CreateBookingRequest request) {
        Map<String, ShowtimeResponse> showtimeCache = new HashMap<>();   // each showtime is fetched once per request
        Map<String, Set<String>> bookedSeatCache = new HashMap<>();
        Set<String> requestedSeats = new HashSet<>();

        Booking booking = new Booking();
        booking.setCustomerId(customerId);
        booking.setBookingDate(LocalDateTime.now());
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        BigDecimal total = BigDecimal.ZERO;

        for (BookingItemRequest item : request.items()) {
            String seat = item.seatCode();

            // BR07: no duplicated seat inside one request
            if (!requestedSeats.add(item.showtimeId() + "#" + seat)) {
                throw ApiException.badRequest("Duplicate seat " + seat + " of showtime " + item.showtimeId() + " in request");
            }

            // BR08 + BR14: ask movie-service through OpenFeign
            ShowtimeResponse st = showtimeCache.computeIfAbsent(item.showtimeId(), this::fetchShowtime);
            validateShowtime(st);
            validateSeat(seat, st);

            // BR09: seat already sold in another CONFIRMED booking?
            Set<String> taken = bookedSeatCache.computeIfAbsent(st.showtimeId(),
                    id -> new HashSet<>(bookingDetailRepository.findSeatCodesByShowtime(id, BookingStatus.CONFIRMED)));
            if (taken.contains(seat)) {
                throw ApiException.conflict("Seat " + seat + " of showtime " + st.showtimeId() + " is already booked");
            }

            // BR10: price comes from the server + snapshot of the movie info
            BookingDetail detail = new BookingDetail();
            detail.setShowtimeId(st.showtimeId());
            detail.setSeatCode(seat);
            detail.setPrice(st.ticketPrice());
            detail.setMovieId(st.movieId());
            detail.setMovieTitle(st.movieTitle());
            detail.setRoomName(st.roomName());
            detail.setShowtimeStart(st.startTime());
            booking.addDetail(detail);

            total = total.add(st.ticketPrice());
        }

        booking.setTotalPrice(total);
        Booking saved = bookingRepository.save(booking);           // cascade saves the details too
        log.info("Booking {} created for customer {} with {} ticket(s), total {}",
                saved.getBookingId(), customerId, saved.getDetails().size(), total);
        return BookingResponse.from(saved);
    }

    // ======================= HELPER =======================

    private ShowtimeResponse fetchShowtime(String showtimeId) {
        try {
            return movieClient.getShowtime(showtimeId);
        } catch (FeignException.NotFound e) {
            throw ApiException.notFound("Showtime not found with id: " + showtimeId);
        } catch (FeignException e) {
            log.error("Cannot call movie-service: {}", e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Movie service is unavailable. Please try again later.");
        }
    }

    private void validateShowtime(ShowtimeResponse st) {
        if (!"SCHEDULED".equals(st.showtimeStatus())) {
            throw ApiException.badRequest("Showtime " + st.showtimeId() + " is not available (" + st.showtimeStatus() + ")");
        }
        if (!st.startTime().isAfter(LocalDateTime.now())) {
            throw ApiException.badRequest("Showtime " + st.showtimeId() + " has already started");
        }
    }

    /** Seat "E5": row E (index 4) must be < seatRows and number 5 must be <= seatsPerRow */
    private void validateSeat(String seat, ShowtimeResponse st) {
        int rowIndex = seat.charAt(0) - 'A';
        int number = Integer.parseInt(seat.substring(1));
        if (rowIndex >= st.seatRows() || number > st.seatsPerRow()) {
            char lastRow = (char) ('A' + st.seatRows() - 1);
            throw ApiException.badRequest("Seat " + seat + " does not exist in room " + st.roomName()
                    + " (rows A-" + lastRow + ", seats 1-" + st.seatsPerRow() + ")");
        }
    }

    /** BR11: a Customer only reaches their own bookings, Admin reaches all */
    private Booking findAccessible(Long bookingId, Long userId, String role) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> ApiException.notFound("Booking not found with id: " + bookingId));
        if (!ROLE_ADMIN.equals(role) && !booking.getCustomerId().equals(userId)) {
            throw ApiException.forbidden("You can only access your own bookings");
        }
        return booking;
    }
}
