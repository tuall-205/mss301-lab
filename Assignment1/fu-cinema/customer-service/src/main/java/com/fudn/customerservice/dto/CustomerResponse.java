package com.fudn.customerservice.dto;

import com.fudn.customerservice.model.Customer;
import com.fudn.customerservice.model.CustomerStatus;

import java.time.LocalDate;

/** Never exposes the password. */
public record CustomerResponse(
        Long customerId,
        String customerName,
        String telephone,
        String email,
        LocalDate customerBirthday,
        CustomerStatus customerStatus) {

    public static CustomerResponse from(Customer c) {
        return new CustomerResponse(c.getCustomerId(), c.getCustomerName(), c.getTelephone(),
                c.getEmail(), c.getCustomerBirthday(), c.getCustomerStatus());
    }
}
