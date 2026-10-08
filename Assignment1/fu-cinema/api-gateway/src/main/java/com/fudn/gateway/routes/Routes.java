package com.fudn.gateway.routes;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static com.fudn.gateway.filter.UserHeaderFilter.forwardUserInfo;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

@Configuration(proxyBeanMethods = false)
public class Routes {

    @Value("${services.customer.url}")
    private String customerServiceUrl;

    @Value("${services.movie.url}")
    private String movieServiceUrl;

    @Value("${services.booking.url}")
    private String bookingServiceUrl;

    @Bean
    public RouterFunction<ServerResponse> customerServiceRoute() {
        return route("customer_service")
                .route(path("/api/auth/**").or(path("/api/customers/**")), http())
                .before(uri(customerServiceUrl))
                .before(forwardUserInfo())
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> movieServiceRoute() {
        return route("movie_service")
                .route(path("/api/genres/**")
                        .or(path("/api/rooms/**"))
                        .or(path("/api/movies/**"))
                        .or(path("/api/showtimes/**")), http())
                .before(uri(movieServiceUrl))
                .before(forwardUserInfo())
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> bookingServiceRoute() {
        return route("booking_service")
                .route(RequestPredicates.path("/api/bookings/**"), http())
                .before(uri(bookingServiceUrl))
                .before(forwardUserInfo())
                .build();
    }
}
