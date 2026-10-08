package com.fudn.bookingservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "booking_detail")
@Getter
@Setter
@NoArgsConstructor
public class BookingDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bookingDetailId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(nullable = false, length = 24)
    private String showtimeId;            // ObjectId from movie-service (MongoDB)

    @Column(nullable = false, length = 5)
    private String seatCode;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    // ---- snapshot from movie-service ----
    @Column(nullable = false, length = 24)
    private String movieId;

    @Column(nullable = false, length = 200)
    private String movieTitle;

    @Column(nullable = false, length = 50)
    private String roomName;

    @Column(nullable = false)
    private LocalDateTime showtimeStart;
}
