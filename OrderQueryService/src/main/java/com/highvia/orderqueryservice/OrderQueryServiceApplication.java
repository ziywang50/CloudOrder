package com.highvia.orderqueryservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class OrderQueryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderQueryServiceApplication.class, args);
    }

}
