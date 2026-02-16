package com.highvia.customerservice.Service;

import com.highvia.customerservice.Entity.CustomerEntity;
import com.highvia.customerservice.Repository.CustomerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CustomerService {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public Optional<CustomerEntity> getCustomerByEmail(String email) {
        return customerRepository.findByEmail(email);
    }
}