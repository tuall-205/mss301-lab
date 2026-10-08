package com.fudn.movieservice.service;

import com.fudn.movieservice.dto.GenreRequest;
import com.fudn.movieservice.dto.GenreResponse;
import com.fudn.movieservice.exception.ApiException;
import com.fudn.movieservice.model.Genre;
import com.fudn.movieservice.repository.GenreRepository;
import com.fudn.movieservice.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GenreService {

    private final GenreRepository genreRepository;
    private final MovieRepository movieRepository;

    public List<GenreResponse> getAll() {
        return genreRepository.findAll(Sort.by("genreName")).stream().map(GenreResponse::from).toList();
    }

    public GenreResponse getById(String id) {
        return GenreResponse.from(find(id));
    }

    public GenreResponse create(GenreRequest request) {
        if (genreRepository.existsByGenreNameIgnoreCase(request.genreName())) {
            throw ApiException.conflict("Genre name already exists: " + request.genreName());
        }
        Genre genre = new Genre();                 // genreId = null -> MongoDB generates the ObjectId
        genre.setGenreName(request.genreName());
        genre.setDescription(request.description());
        return GenreResponse.from(genreRepository.save(genre));
    }

    public GenreResponse update(String id, GenreRequest request) {
        Genre genre = find(id);
        if (genreRepository.existsByGenreNameIgnoreCaseAndGenreIdNot(request.genreName(), id)) {
            throw ApiException.conflict("Genre name already exists: " + request.genreName());
        }
        genre.setGenreName(request.genreName());
        genre.setDescription(request.description());
        return GenreResponse.from(genreRepository.save(genre));
    }

    public void delete(String id) {
        Genre genre = find(id);
        if (movieRepository.existsByGenreId(id)) {             // BR03
            throw ApiException.conflict("Cannot delete genre that still has movies");
        }
        genreRepository.delete(genre);
    }

    /** Shared with MovieService for the BR15 existence check */
    Genre find(String id) {
        return genreRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Genre not found with id: " + id));
    }
}
