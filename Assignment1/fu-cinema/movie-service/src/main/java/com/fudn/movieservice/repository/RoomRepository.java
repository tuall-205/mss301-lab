package com.fudn.movieservice.repository;

import com.fudn.movieservice.model.CinemaRoom;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RoomRepository extends MongoRepository<CinemaRoom, String> {
    boolean existsByRoomNameIgnoreCase(String roomName);
    boolean existsByRoomNameIgnoreCaseAndRoomIdNot(String roomName, String roomId);
}
