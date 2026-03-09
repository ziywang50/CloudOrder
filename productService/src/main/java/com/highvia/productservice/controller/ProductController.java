package com.highvia.productservice.controller;

import com.highvia.productservice.document.ProductDocument;
import com.highvia.productservice.dto.DeductStockRequest;
import com.highvia.productservice.entity.ProductEntity;
import com.highvia.productservice.service.ProductQueryService;
import com.highvia.productservice.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final ProductQueryService productQueryService;

    /*public ProductController(ProductService productService) {
        this.productService = productService;
    }*/

    @GetMapping
    public List<ProductEntity> getAllProducts() {
        return productService.getAllProducts();
    }

    @GetMapping("/{id}")
    public ProductEntity getProduct(@PathVariable Long id) {
        ProductEntity product = productService.getProduct(id);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return product;
    }

    @PostMapping
    public ProductEntity createProduct(@RequestBody ProductEntity product) {
        return productService.createProduct(product);
    }

    @GetMapping("/search")
    public List<ProductDocument> searchProducts(@RequestParam String keyword) {
        return productQueryService.searchProducts(keyword);
    }

    @PutMapping("/{id}/deduct-stock")
    public void deductStock(@PathVariable Long id, @RequestBody DeductStockRequest request) {
        boolean success = productService.deductStock(id, request.quantity());
        if (!success) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Failed to deduct stock");
        }
    }

    @PutMapping("/{id}")
    public ProductEntity updateProduct(@PathVariable Long id,
                                       @RequestBody ProductEntity product) {
        return productService.updateProduct(id, product);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
    }

    @GetMapping("/query/{id}")
    public ProductDocument getProductFromES(@PathVariable Long id) {
        return productQueryService.getProductById(id);
    }

    @GetMapping("/{id}/stock")
    public Integer getStock(@PathVariable Long id) {
        ProductEntity product = productService.getProduct(id);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return product.getStock();
    }

}
