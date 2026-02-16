package com.highvia.seckillservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ProductDTO(
        Long productId,
        @JsonProperty("name")
        String productName,
        Double price,
        String description,
        Integer stock
) {
}
