package com.fudn.bookingservice.repository;

import com.fudn.bookingservice.model.Booking;
import com.fudn.bookingservice.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByCustomerIdOrderByBookingDateDesc(Long customerId);

    List<Booking> findAllByOrderByBookingDateDesc();

    // [from, to) range, newest first
    @Query("""
            select b from Booking b
            where b.bookingStatus = :status
              and b.bookingDate >= :from
              and b.bookingDate < :to
            order by b.bookingDate desc
            """)
    List<Booking> findForReport(@Param("status") BookingStatus status,
                                @Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to);
}
