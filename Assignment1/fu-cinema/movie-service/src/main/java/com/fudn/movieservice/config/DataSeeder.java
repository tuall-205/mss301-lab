package com.fudn.movieservice.config;

import com.fudn.movieservice.model.*;
import com.fudn.movieservice.repository.GenreRepository;
import com.fudn.movieservice.repository.MovieRepository;
import com.fudn.movieservice.repository.RoomRepository;
import com.fudn.movieservice.repository.ShowtimeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Seeds sample data once (skipped when genres already exist). Fixed ObjectIds make Postman tests stable. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    // ---- fixed ids (24-hex ObjectId) ----
    public static final String GENRE_ACTION = "66f000000000000000000001";
    public static final String GENRE_ANIMATION = "66f000000000000000000002";
    public static final String GENRE_HORROR = "66f000000000000000000003";
    public static final String GENRE_ROMANCE = "66f000000000000000000004";
    public static final String GENRE_SCIFI = "66f000000000000000000005";

    public static final String ROOM_01 = "66f100000000000000000001";
    public static final String ROOM_02 = "66f100000000000000000002";
    public static final String ROOM_IMAX = "66f100000000000000000003";
    public static final String ROOM_04_MAINTENANCE = "66f100000000000000000004";

    public static final String MOVIE_GALAXY = "66f200000000000000000001";
    public static final String MOVIE_HAUNTED = "66f200000000000000000002";
    public static final String MOVIE_ROBOT = "66f200000000000000000003";
    public static final String MOVIE_SUMMER_ENDED = "66f200000000000000000004";

    private final GenreRepository genreRepository;
    private final RoomRepository roomRepository;
    private final MovieRepository movieRepository;
    private final ShowtimeRepository showtimeRepository;

    @Override
    public void run(String... args) {
        if (genreRepository.count() > 0) {
            log.info("MongoDB already has seed data - skip seeding");
            return;
        }

        genreRepository.saveAll(List.of(
                new Genre(GENRE_ACTION, "Hành động", "Phim hành động, võ thuật"),
                new Genre(GENRE_ANIMATION, "Hoạt hình", "Phim hoạt hình cho mọi lứa tuổi"),
                new Genre(GENRE_HORROR, "Kinh dị", "Phim kinh dị, giật gân"),
                new Genre(GENRE_ROMANCE, "Tình cảm", "Phim tình cảm, lãng mạn"),
                new Genre(GENRE_SCIFI, "Khoa học viễn tưởng", "Phim khoa học viễn tưởng")));

        roomRepository.saveAll(List.of(
                new CinemaRoom(ROOM_01, "Room 01", RoomType.STANDARD, 8, 10, RoomStatus.ACTIVE),
                new CinemaRoom(ROOM_02, "Room 02", RoomType.THREE_D, 6, 8, RoomStatus.ACTIVE),
                new CinemaRoom(ROOM_IMAX, "IMAX 01", RoomType.IMAX, 10, 12, RoomStatus.ACTIVE),
                new CinemaRoom(ROOM_04_MAINTENANCE, "Room 04", RoomType.STANDARD, 5, 8, RoomStatus.MAINTENANCE)));

        movieRepository.saveAll(List.of(
                new Movie(MOVIE_GALAXY, "Galaxy Rangers", "Đội biệt kích không gian bảo vệ dải ngân hà.",
                        "John Carter", 125, "English", AgeRating.T13, LocalDate.of(2026, 9, 20),
                        GENRE_SCIFI, MovieStatus.NOW_SHOWING),
                new Movie(MOVIE_HAUNTED, "Ngôi Nhà Ma Ám", "Một gia đình chuyển đến căn nhà cổ ở Đà Lạt.",
                        "Trần Hữu Tấn", 100, "Tiếng Việt", AgeRating.T18, LocalDate.of(2026, 9, 27),
                        GENRE_HORROR, MovieStatus.NOW_SHOWING),
                new Movie(MOVIE_ROBOT, "Robot Nhỏ Phiêu Lưu Ký", "Chú robot nhỏ đi tìm đường về nhà.",
                        "Anna Lee", 95, "English", AgeRating.P, LocalDate.of(2026, 11, 15),
                        GENRE_ANIMATION, MovieStatus.COMING_SOON),
                new Movie(MOVIE_SUMMER_ENDED, "Mùa Hè Năm Ấy", "Câu chuyện tình đầu tuổi học trò.",
                        "Nguyễn Quang Dũng", 110, "Tiếng Việt", AgeRating.T16, LocalDate.of(2026, 6, 1),
                        GENRE_ROMANCE, MovieStatus.ENDED)));

        showtimeRepository.saveAll(List.of(
                showtime("66f300000000000000000001", MOVIE_GALAXY, ROOM_01, "2026-12-20T19:00", 125, 95000, ShowtimeStatus.SCHEDULED),
                showtime("66f300000000000000000002", MOVIE_GALAXY, ROOM_IMAX, "2026-12-20T20:00", 125, 150000, ShowtimeStatus.SCHEDULED),
                showtime("66f300000000000000000003", MOVIE_HAUNTED, ROOM_02, "2026-12-21T21:00", 100, 95000, ShowtimeStatus.SCHEDULED),
                showtime("66f300000000000000000004", MOVIE_ROBOT, ROOM_01, "2026-12-22T09:00", 95, 75000, ShowtimeStatus.SCHEDULED),
                showtime("66f300000000000000000005", MOVIE_HAUNTED, ROOM_01, "2026-12-23T19:00", 100, 95000, ShowtimeStatus.CANCELLED)));

        log.info("Seeded MongoDB: {} genres, {} rooms, {} movies, {} showtimes",
                genreRepository.count(), roomRepository.count(), movieRepository.count(), showtimeRepository.count());
    }

    private static Showtime showtime(String id, String movieId, String roomId, String start,
                                     int durationMinutes, long price, ShowtimeStatus status) {
        LocalDateTime startTime = LocalDateTime.parse(start);
        return new Showtime(id, movieId, roomId, startTime, startTime.plusMinutes(durationMinutes),
                BigDecimal.valueOf(price), status);
    }
}
