package com.fudn.customerservice.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record RegisterRequest(
        @NotBlank(message = "Customer name is required")
        @Size(max = 100, message = "Customer name must not exceed 100 characters")
        String customerName,

        @NotBlank(message = "Telephone is required")
        @Pattern(regexp = "^0[0-9]{9}$", message = "Telephone must have 10 digits and start with 0")
        String telephone,

        @NotBlank(message = "Email is required")
        @Email(message = "Email is invalid")
        @Size(max = 100)
        String email,

        @NotNull(message = "Birthday is required")
        @Past(message = "Birthday must be in the past")
        LocalDate customerBirthday,

        @NotBlank(message = "Password is required")
        @Size(min = 6, max = 50, message = "Password must be 6-50 characters")
        String password) {
}
