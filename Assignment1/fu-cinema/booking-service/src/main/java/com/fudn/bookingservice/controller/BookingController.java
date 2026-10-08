package com.fudn.bookingservice.controller;

import com.fudn.bookingservice.dto.BookingResponse;
import com.fudn.bookingservice.dto.CreateBookingRequest;
import com.fudn.bookingservice.dto.SeatMapResponse;
import com.fudn.bookingservice.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private static final String USER_ID = "X-User-Id";
    private static final String USER_ROLE = "X-User-Role";

    private final BookingService bookingService;

    // Public
    @GetMapping("/showtimes/{showtimeId}/seats")
    public SeatMapResponse getSeatMap(@PathVariable String showtimeId) {
        return bookingService.getSeatMap(showtimeId);
    }

    // CUSTOMER
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse create(@RequestHeader(USER_ID) Long userId,
                                  @Valid @RequestBody CreateBookingRequest request) {
        return bookingService.create(userId, request);
    }

    // CUSTOMER
    @GetMapping("/my")
    public List<BookingResponse> getMyBookings(@RequestHeader(USER_ID) Long userId) {
        return bookingService.getMyBookings(userId);
    }

    // ADMIN
    @GetMapping
    public List<BookingResponse> getAll() {
        return bookingService.getAll();
    }

    // Owner or ADMIN
    @GetMapping("/{id}")
    public BookingResponse getById(@PathVariable Long id,
                                   @RequestHeader(USER_ID) Long userId,
                                   @RequestHeader(value = USER_ROLE, required = false) String role) {
        return bookingService.getById(id, userId, role);
    }

    // Owner or ADMIN
    @PutMapping("/{id}/cancel")
    public BookingResponse cancel(@PathVariable Long id,
                                  @RequestHeader(USER_ID) Long userId,
                                  @RequestHeader(value = USER_ROLE, required = false) String role) {
        return bookingService.cancel(id, userId, role);
    }
}
