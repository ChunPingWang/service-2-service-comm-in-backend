package com.poc.order.integration;

import com.poc.order.adapter.out.grpc.ProductGrpcClient;
import com.poc.order.adapter.out.grpc.ProductGrpcMapper;
import com.poc.order.application.port.out.ProductQueryPort.ProductInfo;
import com.poc.order.domain.model.ProductId;
import com.poc.product.grpc.GetProductRequest;
import com.poc.product.grpc.ProductResponse;
import com.poc.product.grpc.ProductServiceGrpc;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Focused unit test for the outbound gRPC adapter ({@link ProductGrpcClient}).
 * <p>
 * A full Spring-context integration test would require a running gRPC server
 * or an embedded server setup. For this PoC, the blocking stub is mocked
 * directly to verify that:
 * <ol>
 *   <li>The correct gRPC request is built from the domain {@link ProductId}.</li>
 *   <li>The gRPC response is correctly mapped to a {@link ProductInfo}.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductGrpcClient (outbound gRPC adapter)")
class OrderGrpcIntegrationTest {

    @Mock
    private ProductServiceGrpc.ProductServiceBlockingStub productServiceStub;

    @InjectMocks
    private ProductGrpcClient productGrpcClient;

    @Test
    @DisplayName("getProduct calls stub and maps response correctly")
    void getProduct_callsStubAndMapsResponse() {
        // Arrange
        String productId = "product-42";
        ProductResponse grpcResponse = ProductResponse.newBuilder()
                .setProductId(productId)
                .setName("Widget")
                .setPrice(19.99)
                .setCurrency("USD")
                .setStock(50)
                .setCategory("electronics")
                .setDescription("A fine widget")
                .build();

        GetProductRequest expectedRequest = GetProductRequest.newBuilder()
                .setProductId(productId)
                .build();

        when(productServiceStub.getProduct(expectedRequest)).thenReturn(grpcResponse);

        // Act
        ProductInfo result = productGrpcClient.getProduct(new ProductId(productId));

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.productId()).isEqualTo(new ProductId(productId));
        assertThat(result.name()).isEqualTo("Widget");
        assertThat(result.price().amount()).isEqualByComparingTo(new BigDecimal("19.99"));
        assertThat(result.price().currency()).isEqualTo("USD");
        assertThat(result.stockQuantity()).isEqualTo(50);

        verify(productServiceStub).getProduct(expectedRequest);
    }

    @Test
    @DisplayName("ProductGrpcMapper.fromGrpcResponse maps all fields correctly")
    void mapper_fromGrpcResponse_mapsAllFields() {
        // Arrange
        ProductResponse grpcResponse = ProductResponse.newBuilder()
                .setProductId("prod-99")
                .setName("Gadget")
                .setPrice(5.50)
                .setCurrency("EUR")
                .setStock(200)
                .setCategory("accessories")
                .setDescription("A useful gadget")
                .build();

        // Act
        ProductInfo result = ProductGrpcMapper.fromGrpcResponse(grpcResponse);

        // Assert
        assertThat(result.productId()).isEqualTo(new ProductId("prod-99"));
        assertThat(result.name()).isEqualTo("Gadget");
        assertThat(result.price().amount()).isEqualByComparingTo(new BigDecimal("5.5"));
        assertThat(result.price().currency()).isEqualTo("EUR");
        assertThat(result.stockQuantity()).isEqualTo(200);
    }
}
