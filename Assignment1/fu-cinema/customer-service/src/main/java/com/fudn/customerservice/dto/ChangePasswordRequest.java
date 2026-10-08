package com.fudn.customerservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Old password is required") String oldPassword,
        @NotBlank(message = "New password is required")
        @Size(min = 6, max = 50, message = "New password must be 6-50 characters") String newPassword) {
}
