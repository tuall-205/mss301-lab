package com.fudn.movieservice.service;

import com.fudn.movieservice.dto.MovieResponse;
import com.fudn.movieservice.exception.ApiException;
import com.fudn.movieservice.model.Genre;
import com.fudn.movieservice.model.Movie;
import com.fudn.movieservice.model.MovieStatus;
import com.fudn.movieservice.repository.GenreRepository;
import com.fudn.movieservice.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MovieService {

    private final MovieRepository movieRepository;
    private final GenreRepository genreRepository;
    private final MongoTemplate mongoTemplate;

    /** Dynamic search with Criteria - a null parameter is simply skipped */
    public List<MovieResponse> search(String keyword, String genreId, MovieStatus status) {
        Query query = new Query();
        if (keyword != null && !keyword.isBlank()) {
            // case-insensitive regex; Pattern.quote so characters like ( . * ? are treated literally
            query.addCriteria(Criteria.where("title").regex(Pattern.quote(keyword.trim()), "i"));
        }
        if (genreId != null && !genreId.isBlank()) {
            query.addCriteria(Criteria.where("genreId").is(genreId));
        }
        if (status != null) {
            query.addCriteria(Criteria.where("movieStatus").is(status));
        }
        query.with(Sort.by("title"));
        return toResponses(mongoTemplate.find(query, Movie.class));
    }

    Movie find(String id) {
        return movieRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Movie not found with id: " + id));
    }

    /** Application-side join: read all genres once, then resolve each genreId to its name (no N+1) */
    private List<MovieResponse> toResponses(List<Movie> movies) {
        Map<String, String> genreNames = genreRepository.findAll().stream()
                .collect(Collectors.toMap(Genre::getGenreId, Genre::getGenreName));
        return movies.stream()
                .map(m -> MovieResponse.from(m, genreNames.get(m.getGenreId())))
                .toList();
    }
}
