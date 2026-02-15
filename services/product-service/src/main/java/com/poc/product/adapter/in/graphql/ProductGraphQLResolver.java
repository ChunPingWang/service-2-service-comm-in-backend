package com.poc.product.adapter.in.graphql;

import com.poc.product.application.InventoryCheckResult;
import com.poc.product.application.port.in.InventoryCheckUseCase;
import com.poc.product.application.port.in.ProductQueryUseCase;
import com.poc.product.domain.model.Product;
import com.poc.product.domain.model.ProductId;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

/**
 * GraphQL inbound adapter for the Product Service.
 * <p>
 * Delegates all business logic to inbound port interfaces
 * ({@link ProductQueryUseCase} and {@link InventoryCheckUseCase}).
 */
@Controller
public class ProductGraphQLResolver {

    private final ProductQueryUseCase productQueryUseCase;
    private final InventoryCheckUseCase inventoryCheckUseCase;

    public ProductGraphQLResolver(ProductQueryUseCase productQueryUseCase,
                                  InventoryCheckUseCase inventoryCheckUseCase) {
        this.productQueryUseCase = productQueryUseCase;
        this.inventoryCheckUseCase = inventoryCheckUseCase;
    }

    @QueryMapping
    public ProductGraphQLMapper.ProductDto product(@Argument String id) {
        return productQueryUseCase.findById(new ProductId(id))
                .map(ProductGraphQLMapper::toProductDto)
                .orElse(null);
    }

    @QueryMapping
    public List<ProductGraphQLMapper.ProductDto> products(
            @Argument String category,
            @Argument Integer limit) {
        int effectiveLimit = (limit != null) ? limit : 10;
        String effectiveCategory = (category != null) ? category : "ELECTRONICS";
        return productQueryUseCase.findByCategory(effectiveCategory, effectiveLimit)
                .stream()
                .map(ProductGraphQLMapper::toProductDto)
                .toList();
    }

    @QueryMapping
    public ProductGraphQLMapper.InventoryCheckDto checkInventory(
            @Argument String productId,
            @Argument int quantity) {
        InventoryCheckResult result = inventoryCheckUseCase.checkInventory(
                new ProductId(productId), quantity);
        return ProductGraphQLMapper.toInventoryCheckDto(result);
    }
}
