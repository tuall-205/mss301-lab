package com.mss301.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    // RestClient.Builder do Spring Boot 4 auto-configure (spring-boot-starter-restclient):
    // đã gắn sẵn Jackson 3 converter và timeout spring.http.clients.*.
    // Bean này là prototype -> mỗi lần inject nhận một builder mới, không ảnh hưởng lẫn nhau.
    @Bean
    public RestClient inventoryRestClient(RestClient.Builder builder,
                                          @Value("${services.inventory.url}") String url) {
        return builder.baseUrl(url).build();
    }

    @Bean
    public RestClient paymentRestClient(RestClient.Builder builder,
                                        @Value("${services.payment.url}") String url) {
        return builder.baseUrl(url).build();
    }
}
