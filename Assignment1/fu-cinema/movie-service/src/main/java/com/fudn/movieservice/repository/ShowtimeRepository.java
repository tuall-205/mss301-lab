package com.fudn.movieservice.repository;

import com.fudn.movieservice.model.Showtime;
import com.fudn.movieservice.model.ShowtimeStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ShowtimeRepository extends MongoRepository<Showtime, String> {

    boolean existsByRoomId(String roomId);

    boolean existsByMovieId(String movieId);

    List<Showtime> findAllByOrderByStartTimeAsc();

    List<Showtime> findByMovieIdOrderByStartTimeAsc(String movieId);

    /**
     * Generated query:
     * { roomId: ?0, showtimeStatus: ?1, startTime: { $lt: ?2 }, endTime: { $gt: ?3 }, _id: { $ne: ?4 } }
     * ?2 = end of the new showtime, ?3 = start of the new showtime, ?4 = id to skip when updating.
     * Two ranges overlap when s1 < e2 AND e1 > s2.
     */
    long countByRoomIdAndShowtimeStatusAndStartTimeLessThanAndEndTimeGreaterThanAndShowtimeIdNot(
            String roomId, ShowtimeStatus status, LocalDateTime newEndTime, LocalDateTime newStartTime,
            String excludeShowtimeId);
}
