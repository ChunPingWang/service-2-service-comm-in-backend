package com.poc.order.adapter.in.rest;

import com.poc.order.application.port.in.CreateOrderUseCase;
import com.poc.order.application.port.in.CreateOrderUseCase.CreateOrderCommand;
import com.poc.order.application.port.in.QueryOrderUseCase;
import com.poc.order.domain.model.Order;
import com.poc.order.domain.model.OrderId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Inbound REST adapter for Order operations.
 * Depends only on inbound use-case ports -- never on application services
 * or outbound ports (enforced by ArchUnit).
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final QueryOrderUseCase queryOrderUseCase;

    public OrderController(CreateOrderUseCase createOrderUseCase,
                           QueryOrderUseCase queryOrderUseCase) {
        this.createOrderUseCase = createOrderUseCase;
        this.queryOrderUseCase = queryOrderUseCase;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestBody OrderRequest request) {
        String orderId = UUID.randomUUID().toString();

        CreateOrderCommand command = new CreateOrderCommand(
                orderId,
                request.customerId(),
                request.productId(),
                request.quantity()
        );

        Order order = createOrderUseCase.createOrder(command);
        OrderResponse response = OrderRestMapper.toResponse(order);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable String orderId) {
        return queryOrderUseCase.findById(new OrderId(orderId))
                .map(OrderRestMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Bad Request",
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString(),
                "traceId", ""
        ));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "Conflict",
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString(),
                "traceId", ""
        ));
    }
}
