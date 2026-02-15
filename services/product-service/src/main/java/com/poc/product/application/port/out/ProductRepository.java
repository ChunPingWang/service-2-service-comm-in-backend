package com.poc.product.application.port.out;

import com.poc.product.domain.model.Category;
import com.poc.product.domain.model.Product;
import com.poc.product.domain.model.ProductId;
import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Optional<Product> findById(ProductId productId);
    List<Product> findByCategory(Category category, int limit);
    Product save(Product product);
}
