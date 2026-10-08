package com.fudn.movieservice.dto;

import com.fudn.movieservice.model.Genre;

public record GenreResponse(String genreId, String genreName, String description) {
    public static GenreResponse from(Genre g) {
        return new GenreResponse(g.getGenreId(), g.getGenreName(), g.getDescription());
    }
}
