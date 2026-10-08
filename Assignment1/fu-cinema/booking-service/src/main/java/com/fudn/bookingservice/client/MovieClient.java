package com.fudn.bookingservice.client;

import com.fudn.bookingservice.dto.ShowtimeResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(value = "movie-service", url = "${movie.service.url}")
public interface MovieClient {

    @GetMapping("/api/showtimes/{id}")
    ShowtimeResponse getShowtime(@PathVariable("id") String id);
}
