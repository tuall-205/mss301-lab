package com.fudn.movieservice.dto;

import com.fudn.movieservice.model.RoomStatus;
import com.fudn.movieservice.model.RoomType;
import jakarta.validation.constraints.*;

public record RoomRequest(
        @NotBlank(message = "Room name is required") @Size(max = 50) String roomName,
        @NotNull(message = "Room type is required") RoomType roomType,
        @NotNull @Min(value = 1, message = "seatRows must be 1-26") @Max(value = 26, message = "seatRows must be 1-26") Integer seatRows,
        @NotNull @Min(value = 1, message = "seatsPerRow must be 1-30") @Max(value = 30, message = "seatsPerRow must be 1-30") Integer seatsPerRow,
        @NotNull(message = "Room status is required") RoomStatus roomStatus) {
}
