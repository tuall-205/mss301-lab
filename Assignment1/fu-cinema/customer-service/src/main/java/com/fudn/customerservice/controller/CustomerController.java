package com.fudn.customerservice.controller;

import com.fudn.customerservice.dto.*;
import com.fudn.customerservice.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private static final String USER_ID = "X-User-Id";

    private final CustomerService customerService;

    // ---------- Public ----------
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse register(@Valid @RequestBody RegisterRequest request) {
        return customerService.register(request);
    }

    // ---------- CUSTOMER ----------
    @GetMapping("/me")
    public CustomerResponse getProfile(@RequestHeader(USER_ID) Long userId) {
        return customerService.getProfile(userId);
    }

    @PutMapping("/me")
    public CustomerResponse updateProfile(@RequestHeader(USER_ID) Long userId,
                                          @Valid @RequestBody ProfileUpdateRequest request) {
        return customerService.updateProfile(userId, request);
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@RequestHeader(USER_ID) Long userId,
                               @Valid @RequestBody ChangePasswordRequest request) {
        customerService.changePassword(userId, request);
    }
}
