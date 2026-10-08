package com.fudn.customerservice.controller;

import com.fudn.customerservice.dto.*;
import com.fudn.customerservice.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    // ---------- ADMIN ----------
    @GetMapping
    public List<CustomerResponse> search(@RequestParam(required = false) String keyword) {
        return customerService.search(keyword);
    }

    @GetMapping("/{id}")
    public CustomerResponse getById(@PathVariable Long id) {
        return customerService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(@Valid @RequestBody AdminCustomerRequest request) {
        return customerService.create(request);
    }

    @PutMapping("/{id}")
    public CustomerResponse update(@PathVariable Long id, @Valid @RequestBody AdminCustomerRequest request) {
        return customerService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        customerService.delete(id);
    }
}
