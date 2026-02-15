package com.poc.product.unit.application;

import com.poc.product.application.InventoryCheckResult;
import com.poc.product.application.port.out.ProductRepository;
import com.poc.product.application.service.ProductApplicationService;
import com.poc.product.domain.model.Category;
import com.poc.product.domain.model.Money;
import com.poc.product.domain.model.Product;
import com.poc.product.domain.model.ProductId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductApplicationService")
class ProductApplicationServiceTest {

    @Mock
    private ProductRepository productRepository;

    private ProductApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ProductApplicationService(productRepository);
    }

    private Product createProduct(String id, String name, int stockQuantity, Category category) {
        return new Product(
                new ProductId(id),
                name,
                "A test product",
                new Money(new BigDecimal("29.99"), "USD"),
                stockQuantity,
                category
        );
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("returns product when it exists")
        void findById_exists() {
            var productId = new ProductId("prod-001");
            var product = createProduct("prod-001", "Laptop", 10, Category.ELECTRONICS);
            when(productRepository.findById(productId)).thenReturn(Optional.of(product));

            Optional<Product> result = service.findById(productId);

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(product);
            verify(productRepository).findById(productId);
        }

        @Test
        @DisplayName("returns empty when product not found")
        void findById_notFound() {
            var productId = new ProductId("prod-999");
            when(productRepository.findById(productId)).thenReturn(Optional.empty());

            Optional<Product> result = service.findById(productId);

            assertThat(result).isEmpty();
            verify(productRepository).findById(productId);
        }
    }

    @Nested
    @DisplayName("findByCategory")
    class FindByCategory {

        @Test
        @DisplayName("returns products for given category")
        void findByCategory_returnsProducts() {
            var product1 = createProduct("prod-001", "Laptop", 10, Category.ELECTRONICS);
            var product2 = createProduct("prod-002", "Phone", 5, Category.ELECTRONICS);
            when(productRepository.findByCategory(Category.ELECTRONICS, 10))
                    .thenReturn(List.of(product1, product2));

            List<Product> result = service.findByCategory("ELECTRONICS", 10);

            assertThat(result).hasSize(2);
            assertThat(result).containsExactly(product1, product2);
            verify(productRepository).findByCategory(Category.ELECTRONICS, 10);
        }

        @Test
        @DisplayName("returns empty list when no products in category")
        void findByCategory_emptyList() {
            when(productRepository.findByCategory(Category.SPORTS, 10))
                    .thenReturn(List.of());

            List<Product> result = service.findByCategory("SPORTS", 10);

            assertThat(result).isEmpty();
            verify(productRepository).findByCategory(Category.SPORTS, 10);
        }
    }

    @Nested
    @DisplayName("checkInventory")
    class CheckInventory {

        @Test
        @DisplayName("returns available when stock is sufficient")
        void checkInventory_available() {
            var productId = new ProductId("prod-001");
            var product = createProduct("prod-001", "Laptop", 10, Category.ELECTRONICS);
            when(productRepository.findById(productId)).thenReturn(Optional.of(product));

            InventoryCheckResult result = service.checkInventory(productId, 5);

            assertThat(result.available()).isTrue();
            assertThat(result.remainingStock()).isEqualTo(10);
            verify(productRepository).findById(productId);
        }

        @Test
        @DisplayName("returns unavailable when stock is insufficient")
        void checkInventory_insufficientStock() {
            var productId = new ProductId("prod-001");
            var product = createProduct("prod-001", "Laptop", 3, Category.ELECTRONICS);
            when(productRepository.findById(productId)).thenReturn(Optional.of(product));

            InventoryCheckResult result = service.checkInventory(productId, 5);

            assertThat(result.available()).isFalse();
            assertThat(result.remainingStock()).isEqualTo(3);
            verify(productRepository).findById(productId);
        }

        @Test
        @DisplayName("throws exception when product not found")
        void checkInventory_productNotFound() {
            var productId = new ProductId("prod-999");
            when(productRepository.findById(productId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.checkInventory(productId, 1))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("prod-999");
            verify(productRepository).findById(productId);
        }
    }
}
