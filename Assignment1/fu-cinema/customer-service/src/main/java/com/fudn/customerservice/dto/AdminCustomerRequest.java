package com.fudn.customerservice.dto;

import com.fudn.customerservice.model.CustomerStatus;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

/** Used by Admin to create/update; password is required on create and optional (null) on update. */
public record AdminCustomerRequest(
        @NotBlank(message = "Customer name is required") @Size(max = 100) String customerName,
        @NotBlank(message = "Telephone is required")
        @Pattern(regexp = "^0[0-9]{9}$", message = "Telephone must have 10 digits and start with 0") String telephone,
        @NotBlank(message = "Email is required") @Email(message = "Email is invalid") @Size(max = 100) String email,
        @NotNull(message = "Birthday is required") @Past(message = "Birthday must be in the past") LocalDate customerBirthday,
        @NotNull(message = "Customer status is required") CustomerStatus customerStatus,
        @Size(min = 6, max = 50, message = "Password must be 6-50 characters") String password) {
}
