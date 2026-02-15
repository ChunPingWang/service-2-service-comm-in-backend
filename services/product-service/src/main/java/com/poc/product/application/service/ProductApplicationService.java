package com.poc.product.application.service;

import com.poc.product.application.InventoryCheckResult;
import com.poc.product.application.port.in.InventoryCheckUseCase;
import com.poc.product.application.port.in.ProductQueryUseCase;
import com.poc.product.application.port.out.ProductRepository;
import com.poc.product.domain.model.Category;
import com.poc.product.domain.model.Product;
import com.poc.product.domain.model.ProductId;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class ProductApplicationService implements ProductQueryUseCase, InventoryCheckUseCase {

    private final ProductRepository productRepository;

    public ProductApplicationService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public Optional<Product> findById(ProductId productId) {
        return productRepository.findById(productId);
    }

    @Override
    public List<Product> findByCategory(String category, int limit) {
        Category parsedCategory = Category.fromString(category);
        return productRepository.findByCategory(parsedCategory, limit);
    }

    @Override
    public InventoryCheckResult checkInventory(ProductId productId, int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Product not found: " + productId.id()));
        boolean available = product.hasAvailableStock(quantity);
        return new InventoryCheckResult(available, product.stockQuantity());
    }
}
