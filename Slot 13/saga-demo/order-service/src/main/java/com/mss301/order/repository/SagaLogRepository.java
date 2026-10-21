package com.mss301.order.repository;

import com.mss301.order.entity.SagaLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SagaLogRepository extends JpaRepository<SagaLog, Long> {
    List<SagaLog> findByOrderIdOrderByIdAsc(Long orderId);
}
