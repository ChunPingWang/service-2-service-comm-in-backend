package com.poc.product.adapter.in.rest;

import com.poc.product.application.InventoryCheckResult;
import com.poc.product.application.port.in.InventoryCheckUseCase;
import com.poc.product.application.port.in.ProductQueryUseCase;
import com.poc.product.domain.model.Product;
import com.poc.product.domain.model.ProductId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST inbound adapter for the Product Service.
 * <p>
 * Delegates all business logic to inbound port interfaces
 * ({@link ProductQueryUseCase} and {@link InventoryCheckUseCase}).
 */
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductQueryUseCase productQueryUseCase;
    private final InventoryCheckUseCase inventoryCheckUseCase;

    public ProductController(ProductQueryUseCase productQueryUseCase,
                             InventoryCheckUseCase inventoryCheckUseCase) {
        this.productQueryUseCase = productQueryUseCase;
        this.inventoryCheckUseCase = inventoryCheckUseCase;
    }

    @GetMapping
    public List<ProductRestMapper.ProductRestResponse> listProducts(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "10") int limit) {
        String effectiveCategory = (category != null) ? category : "ELECTRONICS";
        return productQueryUseCase.findByCategory(effectiveCategory, limit)
                .stream()
                .map(ProductRestMapper::toProductRestResponse)
                .toList();
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ProductRestMapper.ProductRestResponse> getProduct(
            @PathVariable String productId) {
        return productQueryUseCase.findById(new ProductId(productId))
                .map(ProductRestMapper::toProductRestResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{productId}/inventory")
    public ProductRestMapper.InventoryRestResponse checkInventory(
            @PathVariable String productId,
            @RequestParam int quantity) {
        InventoryCheckResult result = inventoryCheckUseCase.checkInventory(
                new ProductId(productId), quantity);
        return ProductRestMapper.toInventoryRestResponse(result);
    }
}
