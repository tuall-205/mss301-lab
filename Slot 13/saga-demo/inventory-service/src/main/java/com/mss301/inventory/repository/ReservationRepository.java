package com.mss301.inventory.repository;

import com.mss301.inventory.entity.Reservation;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ReservationRepository extends MongoRepository<Reservation, Long> {
}
