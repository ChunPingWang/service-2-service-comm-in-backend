package com.poc.order.integration;

import com.poc.order.application.port.in.CreateOrderUseCase;
import com.poc.order.application.port.in.HandleShipmentEventUseCase;
import com.poc.order.application.port.in.QueryOrderUseCase;
import com.poc.order.application.port.out.OrderRepository;
import com.poc.order.application.port.out.PaymentPort;
import com.poc.order.application.port.out.ProductQueryPort;
import com.poc.order.domain.model.CustomerId;
import com.poc.order.domain.model.Money;
import com.poc.order.domain.model.Order;
import com.poc.order.domain.model.OrderId;
import com.poc.order.domain.model.OrderItem;
import com.poc.order.domain.model.ProductId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for the Order REST API.
 * Uses {@code @MockitoBean} (Spring Boot 4) to mock the inbound use-case ports
 * and outbound ports required by the Spring application context.
 * Uses {@link MockMvc} via {@link MockMvcBuilders#webAppContextSetup} for
 * HTTP request/response assertions.
 */
@SpringBootTest
class OrderRestIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @MockitoBean
    private CreateOrderUseCase createOrderUseCase;

    @MockitoBean
    private QueryOrderUseCase queryOrderUseCase;

    @MockitoBean
    private HandleShipmentEventUseCase handleShipmentEventUseCase;

    // Outbound ports must be mocked so the Spring context can start
    @MockitoBean
    private ProductQueryPort productQueryPort;

    @MockitoBean
    private PaymentPort paymentPort;

    @MockitoBean
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    @DisplayName("POST /api/v1/orders returns 201 with valid request")
    void createOrder_returns201() throws Exception {
        // Arrange
        Order stubOrder = Order.create(
                new OrderId("order-123"),
                new CustomerId("customer-456"),
                List.of(new OrderItem(new ProductId("product-789"), 2,
                        new Money(new BigDecimal("29.99"), "USD")))
        );
        when(createOrderUseCase.createOrder(any())).thenReturn(stubOrder);

        String requestJson = """
                {
                    "productId": "product-789",
                    "quantity": 2,
                    "customerId": "customer-456"
                }
                """;

        // Act & Assert
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("order-123"))
                .andExpect(jsonPath("$.customerId").value("customer-456"))
                .andExpect(jsonPath("$.productId").value("product-789"))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.totalAmount.amount").value(59.98))
                .andExpect(jsonPath("$.totalAmount.currency").value("USD"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id} returns 200 when order exists")
    void getOrder_returns200_whenFound() throws Exception {
        // Arrange
        Order stubOrder = Order.create(
                new OrderId("order-999"),
                new CustomerId("customer-111"),
                List.of(new OrderItem(new ProductId("product-222"), 1,
                        new Money(new BigDecimal("10.00"), "USD")))
        );
        when(queryOrderUseCase.findById(new OrderId("order-999")))
                .thenReturn(Optional.of(stubOrder));

        // Act & Assert
        mockMvc.perform(get("/api/v1/orders/order-999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("order-999"))
                .andExpect(jsonPath("$.customerId").value("customer-111"))
                .andExpect(jsonPath("$.productId").value("product-222"))
                .andExpect(jsonPath("$.quantity").value(1))
                .andExpect(jsonPath("$.totalAmount.amount").value(10.0))
                .andExpect(jsonPath("$.totalAmount.currency").value("USD"));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id} returns 404 when order does not exist")
    void getOrder_returns404_whenNotFound() throws Exception {
        // Arrange
        when(queryOrderUseCase.findById(new OrderId("nonexistent")))
                .thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/api/v1/orders/nonexistent"))
                .andExpect(status().isNotFound());
    }
}
