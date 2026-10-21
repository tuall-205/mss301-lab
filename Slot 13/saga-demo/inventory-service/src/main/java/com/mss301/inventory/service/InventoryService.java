package com.mss301.inventory.service;

import com.mss301.inventory.dto.ReserveRequest;
import com.mss301.inventory.entity.*;
import com.mss301.inventory.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final ProductRepository productRepository;
    private final ReservationRepository reservationRepository;

    // LOCAL TRANSACTION của bước 2: trừ kho + ghi reservation
    // (2 collection trong cùng inventorydb -> MongoDB multi-document transaction)
    @Transactional
    public Reservation reserve(ReserveRequest req) {
        // Idempotent: nếu orderId này đã được reserve rồi thì trả lại kết quả cũ
        var existing = reservationRepository.findById(req.orderId());
        if (existing.isPresent()) {
            log.info("Order {} đã được reserve trước đó -> bỏ qua", req.orderId());
            return existing.get();
        }

        Product product = productRepository.findById(req.productId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy sản phẩm " + req.productId()));

        if (product.getStock() < req.quantity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Không đủ hàng: còn " + product.getStock() + ", cần " + req.quantity());
        }

        product.setStock(product.getStock() - req.quantity());
        productRepository.save(product);   // MongoDB KHÔNG có dirty checking như JPA -> phải save

        Reservation r = new Reservation();
        r.setOrderId(req.orderId());
        r.setProductId(req.productId());
        r.setQuantity(req.quantity());
        r.setStatus(ReservationStatus.RESERVED);
        log.info("RESERVE order={} product={} qty={} -> stock còn {}",
                req.orderId(), req.productId(), req.quantity(), product.getStock());
        return reservationRepository.save(r);
    }

    // COMPENSATING TRANSACTION của bước 2: cộng lại kho
    @Transactional
    public void release(Long orderId) {
        var opt = reservationRepository.findById(orderId);
        if (opt.isEmpty() || opt.get().getStatus() == ReservationStatus.RELEASED) {
            // Idempotent: chưa reserve hoặc đã release rồi -> không làm gì
            log.info("RELEASE order={} -> không có gì để hoàn tác", orderId);
            return;
        }
        Reservation r = opt.get();
        Product product = productRepository.findById(r.getProductId()).orElseThrow();
        product.setStock(product.getStock() + r.getQuantity());
        r.setStatus(ReservationStatus.RELEASED);
        productRepository.save(product);
        reservationRepository.save(r);
        log.info("RELEASE order={} -> trả lại {} {}, stock = {}",
                orderId, r.getQuantity(), product.getId(), product.getStock());
    }
}
