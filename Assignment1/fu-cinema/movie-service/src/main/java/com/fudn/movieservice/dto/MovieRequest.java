package com.fudn.movieservice.dto;

import com.fudn.movieservice.model.AgeRating;
import com.fudn.movieservice.model.MovieStatus;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record MovieRequest(
        @NotBlank(message = "Title is required") @Size(max = 200) String title,
        @Size(max = 2000) String description,
        @Size(max = 100) String director,
        @NotNull(message = "Duration is required")
        @Min(value = 30, message = "Duration must be 30-300 minutes")
        @Max(value = 300, message = "Duration must be 30-300 minutes") Integer durationMinutes,
        @Size(max = 50) String language,
        @NotNull(message = "Age rating is required") AgeRating ageRating,
        LocalDate releaseDate,
        @NotBlank(message = "Genre id is required") String genreId,
        @NotNull(message = "Movie status is required") MovieStatus movieStatus) {
}
