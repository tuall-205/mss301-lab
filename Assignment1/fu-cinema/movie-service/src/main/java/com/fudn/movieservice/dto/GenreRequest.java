package com.fudn.movieservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GenreRequest(
        @NotBlank(message = "Genre name is required") @Size(max = 50) String genreName,
        @Size(max = 255) String description) {
}
