package com.fudn.movieservice.service;

import com.fudn.movieservice.dto.RoomRequest;
import com.fudn.movieservice.dto.RoomResponse;
import com.fudn.movieservice.exception.ApiException;
import com.fudn.movieservice.model.CinemaRoom;
import com.fudn.movieservice.repository.RoomRepository;
import com.fudn.movieservice.repository.ShowtimeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final ShowtimeRepository showtimeRepository;

    public List<RoomResponse> getAll() {
        return roomRepository.findAll(Sort.by("roomName")).stream().map(RoomResponse::from).toList();
    }

    public RoomResponse getById(String id) {
        return RoomResponse.from(find(id));
    }

    public RoomResponse create(RoomRequest request) {
        if (roomRepository.existsByRoomNameIgnoreCase(request.roomName())) {
            throw ApiException.conflict("Room name already exists: " + request.roomName());
        }
        CinemaRoom room = new CinemaRoom();
        apply(room, request);
        return RoomResponse.from(roomRepository.save(room));
    }

    public RoomResponse update(String id, RoomRequest request) {
        CinemaRoom room = find(id);
        if (roomRepository.existsByRoomNameIgnoreCaseAndRoomIdNot(request.roomName(), id)) {
            throw ApiException.conflict("Room name already exists: " + request.roomName());
        }
        apply(room, request);
        return RoomResponse.from(roomRepository.save(room));
    }

    public void delete(String id) {
        CinemaRoom room = find(id);
        if (showtimeRepository.existsByRoomId(id)) {             // BR03
            throw ApiException.conflict("Cannot delete room that already has showtimes");
        }
        roomRepository.delete(room);
    }

    CinemaRoom find(String id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Room not found with id: " + id));
    }

    private void apply(CinemaRoom room, RoomRequest request) {
        room.setRoomName(request.roomName());
        room.setRoomType(request.roomType());
        room.setSeatRows(request.seatRows());
        room.setSeatsPerRow(request.seatsPerRow());
        room.setRoomStatus(request.roomStatus());
    }
}
