package com.fudn.movieservice.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "cinema_rooms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CinemaRoom {

    @Id
    private String roomId;

    @Indexed(unique = true)
    private String roomName;

    private RoomType roomType;
    private Integer seatRows;
    private Integer seatsPerRow;
    private RoomStatus roomStatus;

    /** Computed value - Spring Data maps by field, so it is NOT stored in the document */
    public int getTotalSeats() {
        return seatRows * seatsPerRow;
    }
}
