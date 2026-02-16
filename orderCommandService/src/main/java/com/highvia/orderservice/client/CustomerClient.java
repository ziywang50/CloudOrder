package com.highvia.orderservice.client;

import com.highvia.orderservice.dto.CustomerDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "CUSTOMER-SERVICE")
public interface CustomerClient {

    @GetMapping("/{email}")
    CustomerDTO getCustomer(@PathVariable("email") String email);
}
