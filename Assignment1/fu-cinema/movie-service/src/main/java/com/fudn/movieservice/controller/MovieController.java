package com.fudn.movieservice.controller;

import com.fudn.movieservice.dto.MovieRequest;
import com.fudn.movieservice.dto.MovieResponse;
import com.fudn.movieservice.model.MovieStatus;
import com.fudn.movieservice.service.MovieService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/movies")
@RequiredArgsConstructor
public class MovieController {

    private final MovieService movieService;

    @GetMapping
    public List<MovieResponse> search(@RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) String genreId,
                                      @RequestParam(required = false) MovieStatus status) {
        return movieService.search(keyword, genreId, status);
    }

    @GetMapping("/{id}")
    public MovieResponse getById(@PathVariable String id) {
        return movieService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MovieResponse create(@Valid @RequestBody MovieRequest request) {
        return movieService.create(request);
    }

    @PutMapping("/{id}")
    public MovieResponse update(@PathVariable String id, @Valid @RequestBody MovieRequest request) {
        return movieService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        movieService.delete(id);
    }
}
