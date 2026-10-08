package com.fudn.movieservice.service;

import com.fudn.movieservice.dto.MovieRequest;
import com.fudn.movieservice.dto.MovieResponse;
import com.fudn.movieservice.exception.ApiException;
import com.fudn.movieservice.model.Genre;
import com.fudn.movieservice.model.Movie;
import com.fudn.movieservice.model.MovieStatus;
import com.fudn.movieservice.repository.GenreRepository;
import com.fudn.movieservice.repository.MovieRepository;
import com.fudn.movieservice.repository.ShowtimeRepository;
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
    private final ShowtimeRepository showtimeRepository;
    private final GenreService genreService;
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

    public MovieResponse getById(String id) {
        Movie movie = find(id);
        return MovieResponse.from(movie, genreService.find(movie.getGenreId()).getGenreName());
    }

    public MovieResponse create(MovieRequest request) {
        Movie movie = new Movie();
        Genre genre = apply(movie, request);
        return MovieResponse.from(movieRepository.save(movie), genre.getGenreName());
    }

    public MovieResponse update(String id, MovieRequest request) {
        Movie movie = find(id);
        Genre genre = apply(movie, request);
        return MovieResponse.from(movieRepository.save(movie), genre.getGenreName());
    }

    public void delete(String id) {
        Movie movie = find(id);
        if (showtimeRepository.existsByMovieId(id)) {            // BR03
            throw ApiException.conflict("Cannot delete movie that already has showtimes. Set status to ENDED instead.");
        }
        movieRepository.delete(movie);
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

    private Genre apply(Movie movie, MovieRequest request) {
        Genre genre = genreService.find(request.genreId());     // BR15: 404 when the genre does not exist
        movie.setTitle(request.title());
        movie.setDescription(request.description());
        movie.setDirector(request.director());
        movie.setDurationMinutes(request.durationMinutes());
        movie.setLanguage(request.language());
        movie.setAgeRating(request.ageRating());
        movie.setReleaseDate(request.releaseDate());
        movie.setGenreId(genre.getGenreId());
        movie.setMovieStatus(request.movieStatus());
        return genre;
    }
}
