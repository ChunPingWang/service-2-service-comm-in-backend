package com.poc.order.unit.application;

import com.poc.order.application.port.in.CreateOrderUseCase.CreateOrderCommand;
import com.poc.order.application.port.out.OrderRepository;
import com.poc.order.application.port.out.PaymentPort;
import com.poc.order.application.port.out.PaymentPort.PaymentResult;
import com.poc.order.application.port.out.ProductQueryPort;
import com.poc.order.application.port.out.ProductQueryPort.ProductInfo;
import com.poc.order.application.service.OrderApplicationService;
import com.poc.order.domain.model.Money;
import com.poc.order.domain.model.Order;
import com.poc.order.domain.model.OrderId;
import com.poc.order.domain.model.OrderStatus;
import com.poc.order.domain.model.ProductId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderApplicationService")
class OrderApplicationServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductQueryPort productQueryPort;

    @Mock
    private PaymentPort paymentPort;

    private OrderApplicationService sut;

    @BeforeEach
    void setUp() {
        sut = new OrderApplicationService(orderRepository, productQueryPort, paymentPort);
    }

    @Nested
    @DisplayName("createOrder")
    class CreateOrder {

        private static final String ORDER_ID = "order-123";
        private static final String CUSTOMER_ID = "customer-456";
        private static final String PRODUCT_ID = "product-789";
        private static final int QUANTITY = 2;

        private final CreateOrderCommand command =
                new CreateOrderCommand(ORDER_ID, CUSTOMER_ID, PRODUCT_ID, QUANTITY);

        private final ProductInfo validProduct = new ProductInfo(
                new ProductId(PRODUCT_ID),
                "Test Product",
                new Money(new BigDecimal("29.99"), "USD"),
                100
        );

        @Test
        @DisplayName("should create order successfully with payment")
        void createOrder_success() {
            // Arrange
            when(productQueryPort.getProduct(new ProductId(PRODUCT_ID))).thenReturn(validProduct);
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(paymentPort.processPayment(eq(new OrderId(ORDER_ID)), any(Money.class)))
                    .thenReturn(new PaymentResult("pay-001", "SUCCESS"));

            // Act
            Order result = sut.createOrder(command);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.orderId()).isEqualTo(new OrderId(ORDER_ID));
            assertThat(result.customerId().id()).isEqualTo(CUSTOMER_ID);
            assertThat(result.items()).hasSize(1);
            assertThat(result.items().getFirst().productId()).isEqualTo(new ProductId(PRODUCT_ID));
            assertThat(result.items().getFirst().quantity()).isEqualTo(QUANTITY);
            assertThat(result.items().getFirst().unitPrice()).isEqualTo(new Money(new BigDecimal("29.99"), "USD"));
            assertThat(result.status()).isEqualTo(OrderStatus.PAID);
            assertThat(result.totalAmount()).isEqualTo(new Money(new BigDecimal("59.98"), "USD"));

            // Verify interactions
            verify(productQueryPort).getProduct(new ProductId(PRODUCT_ID));
            verify(paymentPort).processPayment(eq(new OrderId(ORDER_ID)), any(Money.class));
            verify(orderRepository, times(3)).save(any(Order.class)); // CREATED, PAYMENT_PENDING, PAID
        }

        @Test
        @DisplayName("should throw when product is not found")
        void createOrder_productNotFound() {
            // Arrange
            when(productQueryPort.getProduct(new ProductId(PRODUCT_ID)))
                    .thenThrow(new RuntimeException("Product not found: " + PRODUCT_ID));

            // Act & Assert
            assertThatThrownBy(() -> sut.createOrder(command))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Product not found");

            // Verify no further interactions after failure
            verify(orderRepository, never()).save(any());
            verify(paymentPort, never()).processPayment(any(), any());
        }

        @Test
        @DisplayName("should throw when stock is insufficient")
        void createOrder_insufficientStock() {
            // Arrange: product has only 1 in stock, but we request 2
            var lowStockProduct = new ProductInfo(
                    new ProductId(PRODUCT_ID),
                    "Test Product",
                    new Money(new BigDecimal("29.99"), "USD"),
                    1 // less than requested quantity of 2
            );
            when(productQueryPort.getProduct(new ProductId(PRODUCT_ID))).thenReturn(lowStockProduct);

            // Act & Assert
            assertThatThrownBy(() -> sut.createOrder(command))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Insufficient stock")
                    .hasMessageContaining(PRODUCT_ID);

            // Verify no order was saved and no payment was attempted
            verify(orderRepository, never()).save(any());
            verify(paymentPort, never()).processPayment(any(), any());
        }

        @Test
        @DisplayName("should keep order in PAYMENT_PENDING when payment fails")
        void createOrder_paymentFails() {
            // Arrange
            when(productQueryPort.getProduct(new ProductId(PRODUCT_ID))).thenReturn(validProduct);
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(paymentPort.processPayment(eq(new OrderId(ORDER_ID)), any(Money.class)))
                    .thenReturn(new PaymentResult("pay-002", "FAILED"));

            // Act
            Order result = sut.createOrder(command);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.orderId()).isEqualTo(new OrderId(ORDER_ID));
            assertThat(result.status()).isEqualTo(OrderStatus.PAYMENT_PENDING);

            // Verify payment was attempted
            verify(paymentPort).processPayment(eq(new OrderId(ORDER_ID)), any(Money.class));
            verify(orderRepository, times(3)).save(any(Order.class)); // CREATED, PAYMENT_PENDING, final save still PAYMENT_PENDING
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("should return order when it exists")
        void findById_exists() {
            // Arrange
            var orderId = new OrderId("order-999");
            var order = Order.create(
                    orderId,
                    new com.poc.order.domain.model.CustomerId("cust-1"),
                    java.util.List.of(
                            new com.poc.order.domain.model.OrderItem(
                                    new ProductId("prod-1"),
                                    1,
                                    new Money(new BigDecimal("10.00"), "USD")
                            )
                    )
            );
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

            // Act
            Optional<Order> result = sut.findById(orderId);

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get().orderId()).isEqualTo(orderId);
            verify(orderRepository).findById(orderId);
        }

        @Test
        @DisplayName("should return empty when order does not exist")
        void findById_notFound() {
            // Arrange
            var orderId = new OrderId("nonexistent-order");
            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

            // Act
            Optional<Order> result = sut.findById(orderId);

            // Assert
            assertThat(result).isEmpty();
            verify(orderRepository).findById(orderId);
        }
    }
}
