package com.poc.order.application.service;

import com.poc.order.application.ShipmentArrangedPayload;
import com.poc.order.application.port.in.CreateOrderUseCase;
import com.poc.order.application.port.in.HandleShipmentEventUseCase;
import com.poc.order.application.port.in.QueryOrderUseCase;
import com.poc.order.application.port.out.OrderRepository;
import com.poc.order.application.port.out.PaymentPort;
import com.poc.order.application.port.out.ProductQueryPort;
import com.poc.order.domain.model.CustomerId;
import com.poc.order.domain.model.Order;
import com.poc.order.domain.model.OrderId;
import com.poc.order.domain.model.OrderItem;
import com.poc.order.domain.model.ProductId;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Application service that orchestrates order creation, query, and shipment event handling.
 * Implements inbound ports and delegates to outbound ports for external concerns.
 */
@Service
public class OrderApplicationService implements CreateOrderUseCase, QueryOrderUseCase, HandleShipmentEventUseCase {

    private final OrderRepository orderRepository;
    private final ProductQueryPort productQueryPort;
    private final PaymentPort paymentPort;

    public OrderApplicationService(OrderRepository orderRepository,
                                   ProductQueryPort productQueryPort,
                                   PaymentPort paymentPort) {
        this.orderRepository = orderRepository;
        this.productQueryPort = productQueryPort;
        this.paymentPort = paymentPort;
    }

    @Override
    public Order createOrder(CreateOrderCommand command) {
        // 1. Query product info via ProductQueryPort
        var productId = new ProductId(command.productId());
        var productInfo = productQueryPort.getProduct(productId);

        // 2. Validate stock availability
        if (productInfo.stockQuantity() < command.quantity()) {
            throw new IllegalStateException(
                    "Insufficient stock for product %s: requested %d, available %d"
                            .formatted(command.productId(), command.quantity(), productInfo.stockQuantity()));
        }

        // 3. Create Order with OrderItem (using product price from ProductQueryPort)
        var orderId = new OrderId(command.orderId());
        var customerId = new CustomerId(command.customerId());
        var orderItem = new OrderItem(productId, command.quantity(), productInfo.price());
        var order = Order.create(orderId, customerId, List.of(orderItem));

        // 4. Save initial order (status: CREATED)
        order = orderRepository.save(order);

        // 5. Transition to PAYMENT_PENDING and process payment
        order = order.markPaymentPending();
        order = orderRepository.save(order);

        var paymentResult = paymentPort.processPayment(orderId, order.totalAmount());

        // 6. Handle payment result
        if ("SUCCESS".equalsIgnoreCase(paymentResult.status())) {
            order = order.markPaid();
        }
        // If payment fails, order stays in PAYMENT_PENDING state

        // 7. Save and return final order
        order = orderRepository.save(order);
        return order;
    }

    @Override
    public Optional<Order> findById(OrderId orderId) {
        return orderRepository.findById(orderId);
    }

    @Override
    public void handleShipmentArranged(ShipmentArrangedPayload payload) {
        OrderId orderId = new OrderId(payload.orderId());
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException(
                        "Order not found: %s".formatted(payload.orderId())));
        Order shippedOrder = order.markShipped();
        orderRepository.save(shippedOrder);
    }
}
