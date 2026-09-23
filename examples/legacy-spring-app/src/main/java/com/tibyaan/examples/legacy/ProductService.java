package com.tibyaan.examples.legacy;

import org.springframework.stereotype.Service;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Product findById(Long id) {
        return productRepository.findById(id).orElse(null);
    }

    // Phase DL-1, fixture RV-2: the one write in the example app. It is never discovered (no rule proves a
    // write); it is drafted, labeled WRITE, and serves only as an approved reviewed capability behind the
    // write gate.
    public Product rename(Long id, String name) {
        Product product = productRepository.findById(id).orElseThrow();
        product.setName(name);
        return productRepository.save(product);
    }
}
