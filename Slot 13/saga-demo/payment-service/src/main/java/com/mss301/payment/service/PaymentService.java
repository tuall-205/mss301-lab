package com.mss301.payment.service;

import com.mss301.payment.dto.PaymentRequest;
import com.mss301.payment.entity.*;
import com.mss301.payment.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final WalletRepository walletRepository;
    private final PaymentRepository paymentRepository;

    // LOCAL TRANSACTION của bước 3: trừ tiền ví + ghi payment (cùng paymentdb trên SQL Server)
    @Transactional
    public Payment pay(PaymentRequest req) {
        var existing = paymentRepository.findById(req.orderId());
        if (existing.isPresent()) {               // idempotent
            return existing.get();
        }

        Wallet wallet = walletRepository.findById(req.customerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy ví của " + req.customerId()));

        if (wallet.getBalance() < req.amount()) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    "Số dư không đủ: còn " + wallet.getBalance() + ", cần " + req.amount());
        }

        wallet.setBalance(wallet.getBalance() - req.amount());   // JPA dirty checking tự UPDATE

        Payment p = new Payment();
        p.setOrderId(req.orderId());
        p.setCustomerId(req.customerId());
        p.setAmount(req.amount());
        p.setStatus(PaymentStatus.PAID);
        log.info("PAY order={} customer={} amount={} -> balance còn {}",
                req.orderId(), req.customerId(), req.amount(), wallet.getBalance());
        return paymentRepository.save(p);
    }

    // COMPENSATING TRANSACTION của bước 3: hoàn tiền
    @Transactional
    public void refund(Long orderId) {
        var opt = paymentRepository.findById(orderId);
        if (opt.isEmpty() || opt.get().getStatus() == PaymentStatus.REFUNDED) {
            log.info("REFUND order={} -> không có gì để hoàn tác", orderId);
            return;
        }
        Payment p = opt.get();
        Wallet wallet = walletRepository.findById(p.getCustomerId()).orElseThrow();
        wallet.setBalance(wallet.getBalance() + p.getAmount());
        p.setStatus(PaymentStatus.REFUNDED);
        log.info("REFUND order={} -> hoàn {} cho {}, balance = {}",
                orderId, p.getAmount(), p.getCustomerId(), wallet.getBalance());
    }
}
