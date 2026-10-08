package com.fudn.movieservice.repository;

import com.fudn.movieservice.model.Genre;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface GenreRepository extends MongoRepository<Genre, String> {
    boolean existsByGenreNameIgnoreCase(String genreName);
    boolean existsByGenreNameIgnoreCaseAndGenreIdNot(String genreName, String genreId);
}
