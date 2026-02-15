package com.poc.product.application.port.in;

import com.poc.product.domain.model.Product;
import com.poc.product.domain.model.ProductId;
import java.util.List;
import java.util.Optional;

public interface ProductQueryUseCase {
    Optional<Product> findById(ProductId productId);
    List<Product> findByCategory(String category, int limit);
}
