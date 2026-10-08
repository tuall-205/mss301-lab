package com.fudn.movieservice.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

@Document(collection = "movies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Movie {

    @Id
    private String movieId;

    private String title;
    private String description;
    private String director;
    private Integer durationMinutes;
    private String language;
    private AgeRating ageRating;
    private LocalDate releaseDate;

    @Indexed                              // speeds up genre lookups and the BR03 check
    private String genreId;

    private MovieStatus movieStatus;
}
