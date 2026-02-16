package com.highvia.customerservice.Controller;

import com.highvia.customerservice.Dto.RegisterDto;
import com.highvia.customerservice.Entity.CustomerEntity;
import com.highvia.customerservice.Service.CustomerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.http.HttpRequest;
import java.util.Optional;

@RestController
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping("/{email}")
    public ResponseEntity<?> getCustomer(@PathVariable String email) {

        /* Circuit Breaker test
        if (Math.random() < 0.5) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Customer Service temporarily unavailable");
        }*/

        Optional<CustomerEntity> customer = customerService.getCustomerByEmail(email);
        return ResponseEntity.ok(customer);
    }
}
