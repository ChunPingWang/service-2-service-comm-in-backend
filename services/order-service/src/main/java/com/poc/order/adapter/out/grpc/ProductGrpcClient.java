package com.poc.order.adapter.out.grpc;

import com.poc.order.application.port.out.ProductQueryPort;
import com.poc.order.domain.model.ProductId;
import com.poc.product.grpc.GetProductRequest;
import com.poc.product.grpc.ProductResponse;
import com.poc.product.grpc.ProductServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

/**
 * Outbound gRPC adapter that implements {@link ProductQueryPort}.
 * Communicates with the Product Service via a blocking gRPC stub,
 * injected by the {@code grpc-client-spring-boot-starter}.
 */
@Component
public class ProductGrpcClient implements ProductQueryPort {

    @GrpcClient("product-service")
    private ProductServiceGrpc.ProductServiceBlockingStub productServiceStub;

    @Override
    public ProductInfo getProduct(ProductId productId) {
        GetProductRequest request = GetProductRequest.newBuilder()
                .setProductId(productId.id())
                .build();

        ProductResponse response = productServiceStub.getProduct(request);

        return ProductGrpcMapper.fromGrpcResponse(response);
    }
}
