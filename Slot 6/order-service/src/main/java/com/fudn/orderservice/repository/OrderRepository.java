package com.fudn.orderservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fudn.orderservice.model.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {
    
}
