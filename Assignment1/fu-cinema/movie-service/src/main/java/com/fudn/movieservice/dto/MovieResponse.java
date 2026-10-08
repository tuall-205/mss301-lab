package com.fudn.movieservice.dto;

import com.fudn.movieservice.model.AgeRating;
import com.fudn.movieservice.model.Movie;
import com.fudn.movieservice.model.MovieStatus;

import java.time.LocalDate;

public record MovieResponse(String movieId, String title, String description, String director,
                            Integer durationMinutes, String language, AgeRating ageRating,
                            LocalDate releaseDate, String genreId, String genreName, MovieStatus movieStatus) {
    public static MovieResponse from(Movie m, String genreName) {
        return new MovieResponse(m.getMovieId(), m.getTitle(), m.getDescription(), m.getDirector(),
                m.getDurationMinutes(), m.getLanguage(), m.getAgeRating(), m.getReleaseDate(),
                m.getGenreId(), genreName, m.getMovieStatus());
    }
}
