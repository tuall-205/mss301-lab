package com.fudn.bookingservice.repository;

import com.fudn.bookingservice.model.BookingDetail;
import com.fudn.bookingservice.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookingDetailRepository extends JpaRepository<BookingDetail, Long> {

    /** Seats already sold for a showtime (only CONFIRMED bookings count) */
    @Query("""
            select d.seatCode from BookingDetail d
            where d.showtimeId = :showtimeId
              and d.booking.bookingStatus = :status
            """)
    List<String> findSeatCodesByShowtime(@Param("showtimeId") String showtimeId,
                                         @Param("status") BookingStatus status);
}
