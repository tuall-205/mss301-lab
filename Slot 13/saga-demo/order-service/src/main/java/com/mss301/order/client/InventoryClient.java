package com.mss301.order.client;

import com.mss301.order.dto.ReserveRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class InventoryClient {

    private final RestClient restClient;

    public InventoryClient(@Qualifier("inventoryRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public void reserve(Long orderId, String productId, int quantity) {
        restClient.post()
                .uri("/api/inventory/reserve")
                .body(new ReserveRequest(orderId, productId, quantity))
                .retrieve()          // 4xx/5xx -> ném RestClientResponseException
                .toBodilessEntity();
    }

    public void release(Long orderId) {
        restClient.post()
                .uri("/api/inventory/release/{orderId}", orderId)
                .retrieve()
                .toBodilessEntity();
    }
}
