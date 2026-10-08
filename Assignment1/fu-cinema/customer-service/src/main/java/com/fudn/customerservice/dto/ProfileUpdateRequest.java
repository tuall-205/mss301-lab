package com.fudn.customerservice.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record ProfileUpdateRequest(
        @NotBlank(message = "Customer name is required") @Size(max = 100) String customerName,
        @NotBlank(message = "Telephone is required")
        @Pattern(regexp = "^0[0-9]{9}$", message = "Telephone must have 10 digits and start with 0") String telephone,
        @NotNull(message = "Birthday is required") @Past(message = "Birthday must be in the past") LocalDate customerBirthday) {
}
