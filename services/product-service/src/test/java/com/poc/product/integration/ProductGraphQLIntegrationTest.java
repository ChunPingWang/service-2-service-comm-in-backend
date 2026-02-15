package com.poc.product.integration;

import com.poc.product.application.InventoryCheckResult;
import com.poc.product.application.port.in.InventoryCheckUseCase;
import com.poc.product.application.port.in.ProductQueryUseCase;
import com.poc.product.domain.model.Category;
import com.poc.product.domain.model.Money;
import com.poc.product.domain.model.Product;
import com.poc.product.domain.model.ProductId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Integration test for the GraphQL inbound adapter.
 * <p>
 * Builds an {@link HttpGraphQlTester} from a {@link WebTestClient} pointing
 * at the randomly-assigned server port.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "grpc.server.port=-1",
                "grpc.server.in-process-name=graphql-test"
        }
)
class ProductGraphQLIntegrationTest {

    private HttpGraphQlTester graphQlTester;

    @MockitoBean
    private ProductQueryUseCase productQueryUseCase;

    @MockitoBean
    private InventoryCheckUseCase inventoryCheckUseCase;

    @BeforeEach
    void setUp(@LocalServerPort int port) {
        WebTestClient webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port + "/graphql")
                .build();
        this.graphQlTester = HttpGraphQlTester.create(webTestClient);
    }

    private static Product sampleProduct() {
        return new Product(
                new ProductId("prod-001"),
                "Test Laptop",
                "A powerful laptop",
                new Money(new BigDecimal("999.99"), "USD"),
                50,
                Category.ELECTRONICS
        );
    }

    @Test
    void product_shouldReturnProduct_whenExists() {
        Product product = sampleProduct();
        when(productQueryUseCase.findById(any(ProductId.class)))
                .thenReturn(Optional.of(product));

        graphQlTester.document("""
                        query {
                          product(id: "prod-001") {
                            id
                            name
                            description
                            price
                            currency
                            stock
                            category
                          }
                        }
                        """)
                .execute()
                .path("product.id").entity(String.class).isEqualTo("prod-001")
                .path("product.name").entity(String.class).isEqualTo("Test Laptop")
                .path("product.description").entity(String.class).isEqualTo("A powerful laptop")
                .path("product.price").entity(Double.class).isEqualTo(999.99)
                .path("product.currency").entity(String.class).isEqualTo("USD")
                .path("product.stock").entity(Integer.class).isEqualTo(50)
                .path("product.category").entity(String.class).isEqualTo("ELECTRONICS");
    }

    @Test
    void product_shouldReturnNull_whenNotExists() {
        when(productQueryUseCase.findById(any(ProductId.class)))
                .thenReturn(Optional.empty());

        graphQlTester.document("""
                        query {
                          product(id: "non-existent") {
                            id
                            name
                          }
                        }
                        """)
                .execute()
                .path("product").valueIsNull();
    }

    @Test
    void products_shouldReturnProductList() {
        Product product = sampleProduct();
        when(productQueryUseCase.findByCategory(eq("ELECTRONICS"), eq(5)))
                .thenReturn(List.of(product));

        graphQlTester.document("""
                        query {
                          products(category: "ELECTRONICS", limit: 5) {
                            id
                            name
                            category
                          }
                        }
                        """)
                .execute()
                .path("products").entityList(Object.class).hasSize(1)
                .path("products[0].id").entity(String.class).isEqualTo("prod-001")
                .path("products[0].name").entity(String.class).isEqualTo("Test Laptop")
                .path("products[0].category").entity(String.class).isEqualTo("ELECTRONICS");
    }

    @Test
    void products_shouldReturnEmptyList_whenNoCategoryMatch() {
        when(productQueryUseCase.findByCategory(eq("SPORTS"), eq(10)))
                .thenReturn(List.of());

        graphQlTester.document("""
                        query {
                          products(category: "SPORTS", limit: 10) {
                            id
                            name
                          }
                        }
                        """)
                .execute()
                .path("products").entityList(Object.class).hasSize(0);
    }

    @Test
    void checkInventory_shouldReturnAvailable() {
        when(inventoryCheckUseCase.checkInventory(any(ProductId.class), eq(5)))
                .thenReturn(new InventoryCheckResult(true, 50));

        graphQlTester.document("""
                        query {
                          checkInventory(productId: "prod-001", quantity: 5) {
                            available
                            remainingStock
                          }
                        }
                        """)
                .execute()
                .path("checkInventory.available").entity(Boolean.class).isEqualTo(true)
                .path("checkInventory.remainingStock").entity(Integer.class).isEqualTo(50);
    }

    @Test
    void checkInventory_shouldReturnUnavailable() {
        when(inventoryCheckUseCase.checkInventory(any(ProductId.class), eq(100)))
                .thenReturn(new InventoryCheckResult(false, 50));

        graphQlTester.document("""
                        query {
                          checkInventory(productId: "prod-001", quantity: 100) {
                            available
                            remainingStock
                          }
                        }
                        """)
                .execute()
                .path("checkInventory.available").entity(Boolean.class).isEqualTo(false)
                .path("checkInventory.remainingStock").entity(Integer.class).isEqualTo(50);
    }
}
