package com.fudn.customerservice.service;

import com.fudn.customerservice.dto.*;
import com.fudn.customerservice.exception.ApiException;
import com.fudn.customerservice.model.Customer;
import com.fudn.customerservice.model.CustomerStatus;
import com.fudn.customerservice.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email}")
    private String adminEmail;

    // ===================== CUSTOMER (F2) =====================

    @Transactional
    public CustomerResponse register(RegisterRequest request) {
        ensureEmailAvailable(request.email(), null);
        Customer customer = new Customer();
        customer.setCustomerName(request.customerName());
        customer.setTelephone(request.telephone());
        customer.setEmail(request.email());
        customer.setCustomerBirthday(request.customerBirthday());
        customer.setCustomerStatus(CustomerStatus.ACTIVE);
        customer.setPassword(passwordEncoder.encode(request.password()));
        Customer saved = customerRepository.save(customer);
        log.info("Customer registered: id={}, email={}", saved.getCustomerId(), saved.getEmail());
        return CustomerResponse.from(saved);
    }

    // ===================== HELPER =====================

    private Customer findCustomer(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Customer not found with id: " + id));
    }

    /** BR01: email must be unique and different from the Admin email. excludeId != null on update. */
    private void ensureEmailAvailable(String email, Long excludeId) {
        boolean exists = excludeId == null
                ? customerRepository.existsByEmailIgnoreCase(email)
                : customerRepository.existsByEmailIgnoreCaseAndCustomerIdNot(email, excludeId);
        if (exists || adminEmail.equalsIgnoreCase(email)) {
            throw ApiException.conflict("Email is already in use: " + email);
        }
    }
}
