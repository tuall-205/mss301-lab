CREATE TABLE booking (
    booking_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_date   DATETIME      NOT NULL,
    total_price    DECIMAL(12,2) NOT NULL,
    customer_id    BIGINT        NOT NULL,   -- logical reference to customer-service (no FK)
    booking_status VARCHAR(20)   NOT NULL,
    INDEX idx_booking_customer (customer_id),
    INDEX idx_booking_date (booking_date)
);

CREATE TABLE booking_detail (
    booking_detail_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id        BIGINT        NOT NULL,
    showtime_id       VARCHAR(24)   NOT NULL,  -- MongoDB ObjectId from movie-service, logical reference
    seat_code         VARCHAR(5)    NOT NULL,
    price             DECIMAL(10,2) NOT NULL,
    -- snapshot taken when the ticket is booked
    movie_id          VARCHAR(24)   NOT NULL,
    movie_title       VARCHAR(200)  NOT NULL,
    room_name         VARCHAR(50)   NOT NULL,
    showtime_start    DATETIME      NOT NULL,
    CONSTRAINT fk_detail_booking FOREIGN KEY (booking_id) REFERENCES booking (booking_id),
    INDEX idx_detail_showtime_seat (showtime_id, seat_code)
);
