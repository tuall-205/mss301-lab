package com.mss301.order.client;

import com.mss301.order.dto.PaymentRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PaymentClient {

    private final RestClient restClient;

    public PaymentClient(@Qualifier("paymentRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public void pay(Long orderId, String customerId, double amount) {
        restClient.post()
                .uri("/api/payments/pay")
                .body(new PaymentRequest(orderId, customerId, amount))
                .retrieve()
                .toBodilessEntity();
    }

    public void refund(Long orderId) {
        restClient.post()
                .uri("/api/payments/refund/{orderId}", orderId)
                .retrieve()
                .toBodilessEntity();
    }
}
