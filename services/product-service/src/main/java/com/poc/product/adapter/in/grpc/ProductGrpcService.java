package com.poc.product.adapter.in.grpc;

import com.poc.product.application.InventoryCheckResult;
import com.poc.product.application.port.in.InventoryCheckUseCase;
import com.poc.product.application.port.in.ProductQueryUseCase;
import com.poc.product.domain.model.Product;
import com.poc.product.domain.model.ProductId;
import com.poc.product.grpc.CheckInventoryRequest;
import com.poc.product.grpc.GetProductRequest;
import com.poc.product.grpc.InventoryResponse;
import com.poc.product.grpc.ListProductsRequest;
import com.poc.product.grpc.ListProductsResponse;
import com.poc.product.grpc.ProductResponse;
import com.poc.product.grpc.ProductServiceGrpc;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;
import java.util.Optional;

/**
 * gRPC inbound adapter for the Product Service.
 * <p>
 * Delegates all business logic to inbound port interfaces
 * ({@link ProductQueryUseCase} and {@link InventoryCheckUseCase}).
 */
@GrpcService
public class ProductGrpcService extends ProductServiceGrpc.ProductServiceImplBase {

    private final ProductQueryUseCase productQueryUseCase;
    private final InventoryCheckUseCase inventoryCheckUseCase;

    public ProductGrpcService(ProductQueryUseCase productQueryUseCase,
                              InventoryCheckUseCase inventoryCheckUseCase) {
        this.productQueryUseCase = productQueryUseCase;
        this.inventoryCheckUseCase = inventoryCheckUseCase;
    }

    @Override
    public void getProduct(GetProductRequest request,
                           StreamObserver<ProductResponse> responseObserver) {
        Optional<Product> product = productQueryUseCase.findById(
                new ProductId(request.getProductId()));

        if (product.isPresent()) {
            responseObserver.onNext(ProductGrpcMapper.toProductResponse(product.get()));
            responseObserver.onCompleted();
        } else {
            responseObserver.onError(
                    Status.NOT_FOUND
                            .withDescription("Product not found: " + request.getProductId())
                            .asRuntimeException());
        }
    }

    @Override
    public void checkInventory(CheckInventoryRequest request,
                               StreamObserver<InventoryResponse> responseObserver) {
        try {
            InventoryCheckResult result = inventoryCheckUseCase.checkInventory(
                    new ProductId(request.getProductId()),
                    request.getQuantity());

            responseObserver.onNext(ProductGrpcMapper.toInventoryResponse(result));
            responseObserver.onCompleted();
        } catch (java.util.NoSuchElementException e) {
            responseObserver.onError(
                    Status.NOT_FOUND
                            .withDescription(e.getMessage())
                            .asRuntimeException());
        }
    }

    @Override
    public void listProducts(ListProductsRequest request,
                             StreamObserver<ListProductsResponse> responseObserver) {
        List<Product> products = productQueryUseCase.findByCategory(
                request.getCategory(),
                request.getLimit());

        ListProductsResponse response = ListProductsResponse.newBuilder()
                .addAllProducts(
                        products.stream()
                                .map(ProductGrpcMapper::toProductResponse)
                                .toList())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
