package com.tibyaan.runtime.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;

import java.lang.reflect.Method;

public class ProductToolHandler {

    private final Object productService;
    private final Method findByIdMethod;
    private final ObjectMapper objectMapper;

    public ProductToolHandler(Object productService, ObjectMapper objectMapper) {
        this.productService = productService;
        this.objectMapper = objectMapper;
        try {
            this.findByIdMethod = productService.getClass().getMethod("findById", Long.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("ProductService must have a findById(Long) method", e);
        }
    }

    @Tool(description = "Get a product by its ID from the database. Returns the product as JSON with id, name, and price fields.")
    public String getProduct(Long id) {
        try {
            Object product = findByIdMethod.invoke(productService, id);
            if (product == null) {
                return "Product not found for id: " + id;
            }
            return objectMapper.writeValueAsString(product);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke ProductService.findById(" + id + ")", e);
        }
    }
}
