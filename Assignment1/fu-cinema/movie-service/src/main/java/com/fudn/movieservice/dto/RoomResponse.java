package com.fudn.movieservice.dto;

import com.fudn.movieservice.model.CinemaRoom;
import com.fudn.movieservice.model.RoomStatus;
import com.fudn.movieservice.model.RoomType;

public record RoomResponse(String roomId, String roomName, RoomType roomType,
                           int seatRows, int seatsPerRow, int totalSeats, RoomStatus roomStatus) {
    public static RoomResponse from(CinemaRoom r) {
        return new RoomResponse(r.getRoomId(), r.getRoomName(), r.getRoomType(),
                r.getSeatRows(), r.getSeatsPerRow(), r.getTotalSeats(), r.getRoomStatus());
    }
}
