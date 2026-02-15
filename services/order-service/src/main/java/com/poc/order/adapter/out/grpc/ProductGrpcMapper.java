package com.poc.order.adapter.out.grpc;

import com.poc.order.application.port.out.ProductQueryPort.ProductInfo;
import com.poc.order.domain.model.Money;
import com.poc.order.domain.model.ProductId;
import com.poc.product.grpc.ProductResponse;

import java.math.BigDecimal;

/**
 * Maps between gRPC {@link ProductResponse} messages and the domain's
 * {@link ProductInfo} projection.
 * Stateless utility class -- all methods are static.
 */
public final class ProductGrpcMapper {

    private ProductGrpcMapper() {
        // prevent instantiation
    }

    /**
     * Converts a gRPC {@link ProductResponse} to a domain {@link ProductInfo}.
     *
     * @param response the gRPC response from the Product Service
     * @return a domain-compatible product information projection
     */
    public static ProductInfo fromGrpcResponse(ProductResponse response) {
        return new ProductInfo(
                new ProductId(response.getProductId()),
                response.getName(),
                new Money(BigDecimal.valueOf(response.getPrice()), response.getCurrency()),
                response.getStock()
        );
    }
}
