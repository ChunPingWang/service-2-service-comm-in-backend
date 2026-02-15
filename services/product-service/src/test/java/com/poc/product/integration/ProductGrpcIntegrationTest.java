package com.poc.product.integration;

import com.poc.product.application.InventoryCheckResult;
import com.poc.product.application.port.in.InventoryCheckUseCase;
import com.poc.product.application.port.in.ProductQueryUseCase;
import com.poc.product.domain.model.Category;
import com.poc.product.domain.model.Money;
import com.poc.product.domain.model.Product;
import com.poc.product.domain.model.ProductId;
import com.poc.product.grpc.CheckInventoryRequest;
import com.poc.product.grpc.GetProductRequest;
import com.poc.product.grpc.InventoryResponse;
import com.poc.product.grpc.ListProductsRequest;
import com.poc.product.grpc.ListProductsResponse;
import com.poc.product.grpc.ProductResponse;
import com.poc.product.grpc.ProductServiceGrpc;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Integration test for the gRPC inbound adapter.
 * <p>
 * Uses in-process gRPC server provided by grpc-server-spring-boot-starter
 * and a blocking stub injected via {@link GrpcClient}.
 */
@SpringBootTest(properties = {
        "grpc.server.in-process-name=test",
        "grpc.server.port=-1",
        "grpc.client.inProcess.address=in-process:test"
})
@DirtiesContext
class ProductGrpcIntegrationTest {

    @GrpcClient("inProcess")
    private ProductServiceGrpc.ProductServiceBlockingStub productStub;

    @MockitoBean
    private ProductQueryUseCase productQueryUseCase;

    @MockitoBean
    private InventoryCheckUseCase inventoryCheckUseCase;

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
    void getProduct_shouldReturnProduct_whenExists() {
        Product product = sampleProduct();
        when(productQueryUseCase.findById(any(ProductId.class)))
                .thenReturn(Optional.of(product));

        ProductResponse response = productStub.getProduct(
                GetProductRequest.newBuilder()
                        .setProductId("prod-001")
                        .build());

        assertThat(response.getProductId()).isEqualTo("prod-001");
        assertThat(response.getName()).isEqualTo("Test Laptop");
        assertThat(response.getDescription()).isEqualTo("A powerful laptop");
        assertThat(response.getPrice()).isEqualTo(999.99);
        assertThat(response.getCurrency()).isEqualTo("USD");
        assertThat(response.getStock()).isEqualTo(50);
        assertThat(response.getCategory()).isEqualTo("ELECTRONICS");
    }

    @Test
    void getProduct_shouldReturnNotFound_whenProductDoesNotExist() {
        when(productQueryUseCase.findById(any(ProductId.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> productStub.getProduct(
                GetProductRequest.newBuilder()
                        .setProductId("non-existent")
                        .build()))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("NOT_FOUND");
    }

    @Test
    void checkInventory_shouldReturnAvailable_whenStockSufficient() {
        when(inventoryCheckUseCase.checkInventory(any(ProductId.class), eq(5)))
                .thenReturn(new InventoryCheckResult(true, 50));

        InventoryResponse response = productStub.checkInventory(
                CheckInventoryRequest.newBuilder()
                        .setProductId("prod-001")
                        .setQuantity(5)
                        .build());

        assertThat(response.getAvailable()).isTrue();
        assertThat(response.getRemainingStock()).isEqualTo(50);
    }

    @Test
    void checkInventory_shouldReturnUnavailable_whenStockInsufficient() {
        when(inventoryCheckUseCase.checkInventory(any(ProductId.class), eq(100)))
                .thenReturn(new InventoryCheckResult(false, 50));

        InventoryResponse response = productStub.checkInventory(
                CheckInventoryRequest.newBuilder()
                        .setProductId("prod-001")
                        .setQuantity(100)
                        .build());

        assertThat(response.getAvailable()).isFalse();
        assertThat(response.getRemainingStock()).isEqualTo(50);
    }

    @Test
    void listProducts_shouldReturnProducts_whenCategoryMatches() {
        Product product = sampleProduct();
        when(productQueryUseCase.findByCategory(eq("ELECTRONICS"), eq(5)))
                .thenReturn(List.of(product));

        ListProductsResponse response = productStub.listProducts(
                ListProductsRequest.newBuilder()
                        .setCategory("ELECTRONICS")
                        .setLimit(5)
                        .build());

        assertThat(response.getProductsList()).hasSize(1);
        ProductResponse first = response.getProducts(0);
        assertThat(first.getProductId()).isEqualTo("prod-001");
        assertThat(first.getName()).isEqualTo("Test Laptop");
        assertThat(first.getCategory()).isEqualTo("ELECTRONICS");
    }

    @Test
    void listProducts_shouldReturnEmptyList_whenNoCategoryMatch() {
        when(productQueryUseCase.findByCategory(eq("SPORTS"), eq(10)))
                .thenReturn(List.of());

        ListProductsResponse response = productStub.listProducts(
                ListProductsRequest.newBuilder()
                        .setCategory("SPORTS")
                        .setLimit(10)
                        .build());

        assertThat(response.getProductsList()).isEmpty();
    }
}
