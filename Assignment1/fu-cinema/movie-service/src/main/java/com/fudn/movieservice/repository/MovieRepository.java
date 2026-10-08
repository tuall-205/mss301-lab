package com.fudn.movieservice.repository;

import com.fudn.movieservice.model.Movie;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MovieRepository extends MongoRepository<Movie, String> {
    boolean existsByGenreId(String genreId);
}
