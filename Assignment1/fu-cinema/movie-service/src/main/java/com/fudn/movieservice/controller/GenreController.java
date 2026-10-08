package com.fudn.movieservice.controller;

import com.fudn.movieservice.dto.GenreRequest;
import com.fudn.movieservice.dto.GenreResponse;
import com.fudn.movieservice.service.GenreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/genres")
@RequiredArgsConstructor
public class GenreController {

    private final GenreService genreService;

    @GetMapping
    public List<GenreResponse> getAll() {
        return genreService.getAll();
    }

    @GetMapping("/{id}")
    public GenreResponse getById(@PathVariable String id) {
        return genreService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GenreResponse create(@Valid @RequestBody GenreRequest request) {
        return genreService.create(request);
    }

    @PutMapping("/{id}")
    public GenreResponse update(@PathVariable String id, @Valid @RequestBody GenreRequest request) {
        return genreService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        genreService.delete(id);
    }
}
