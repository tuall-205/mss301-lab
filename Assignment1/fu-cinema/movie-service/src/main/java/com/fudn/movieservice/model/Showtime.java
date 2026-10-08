package com.fudn.movieservice.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Document(collection = "showtimes")
@CompoundIndex(name = "room_start_idx", def = "{'roomId': 1, 'startTime': 1}")   // serves the overlap query
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Showtime {

    @Id
    private String showtimeId;

    @Indexed
    private String movieId;

    private String roomId;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    // Spring Data stores BigDecimal as String by default; Decimal128 keeps it numeric
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal ticketPrice;

    private ShowtimeStatus showtimeStatus;
}
