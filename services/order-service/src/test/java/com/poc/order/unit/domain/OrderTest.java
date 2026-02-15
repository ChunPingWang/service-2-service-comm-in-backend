package com.poc.order.unit.domain;

import com.poc.order.domain.model.CustomerId;
import com.poc.order.domain.model.Money;
import com.poc.order.domain.model.Order;
import com.poc.order.domain.model.OrderId;
import com.poc.order.domain.model.OrderItem;
import com.poc.order.domain.model.OrderStatus;
import com.poc.order.domain.model.ProductId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderTest {

    // ── Helper factories ─────────────────────────────────────────────────────

    private static Money usd(String amount) {
        return new Money(new BigDecimal(amount), "USD");
    }

    private static OrderItem item(String productId, int quantity, String unitPrice) {
        return new OrderItem(new ProductId(productId), quantity, usd(unitPrice));
    }

    private static List<OrderItem> sampleItems() {
        return List.of(
                item("PROD-1", 2, "10.00"),
                item("PROD-2", 1, "25.50")
        );
    }

    // ── Order.create() ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Order.create()")
    class CreateTests {

        @Test
        @DisplayName("creates order in CREATED status with correct fields")
        void creates_order_in_created_status_with_correct_fields() {
            var orderId = new OrderId("ORD-001");
            var customerId = new CustomerId("CUST-001");
            var items = sampleItems();

            Order order = Order.create(orderId, customerId, items);

            assertEquals(orderId, order.orderId());
            assertEquals(customerId, order.customerId());
            assertEquals(items, order.items());
            assertEquals(OrderStatus.CREATED, order.status());
            assertNotNull(order.createdAt());
            assertNotNull(order.updatedAt());
        }

        @Test
        @DisplayName("fails with null items")
        void fails_with_null_items() {
            assertThrows(IllegalArgumentException.class, () ->
                    Order.create(new OrderId("ORD-001"), new CustomerId("CUST-001"), null));
        }

        @Test
        @DisplayName("fails with empty items")
        void fails_with_empty_items() {
            assertThrows(IllegalArgumentException.class, () ->
                    Order.create(new OrderId("ORD-001"), new CustomerId("CUST-001"), List.of()));
        }

        @Test
        @DisplayName("fails with null orderId")
        void fails_with_null_order_id() {
            assertThrows(IllegalArgumentException.class, () ->
                    Order.create(null, new CustomerId("CUST-001"), sampleItems()));
        }

        @Test
        @DisplayName("fails with null customerId")
        void fails_with_null_customer_id() {
            assertThrows(IllegalArgumentException.class, () ->
                    Order.create(new OrderId("ORD-001"), null, sampleItems()));
        }
    }

    // ── State transitions ────────────────────────────────────────────────────

    @Nested
    @DisplayName("State transitions")
    class StateTransitionTests {

        @Test
        @DisplayName("markPaymentPending() transitions from CREATED to PAYMENT_PENDING")
        void mark_payment_pending_from_created() {
            Order order = Order.create(new OrderId("ORD-001"), new CustomerId("CUST-001"), sampleItems());

            Order updated = order.markPaymentPending();

            assertEquals(OrderStatus.PAYMENT_PENDING, updated.status());
            assertNotNull(updated.updatedAt());
        }

        @Test
        @DisplayName("markPaymentPending() fails from non-CREATED status")
        void mark_payment_pending_fails_from_non_created() {
            Order order = Order.create(new OrderId("ORD-001"), new CustomerId("CUST-001"), sampleItems())
                    .markPaymentPending();

            assertThrows(IllegalStateException.class, order::markPaymentPending);
        }

        @Test
        @DisplayName("markPaid() transitions from PAYMENT_PENDING to PAID")
        void mark_paid_from_payment_pending() {
            Order order = Order.create(new OrderId("ORD-001"), new CustomerId("CUST-001"), sampleItems())
                    .markPaymentPending();

            Order updated = order.markPaid();

            assertEquals(OrderStatus.PAID, updated.status());
        }

        @Test
        @DisplayName("markPaid() fails from non-PAYMENT_PENDING status")
        void mark_paid_fails_from_non_payment_pending() {
            Order order = Order.create(new OrderId("ORD-001"), new CustomerId("CUST-001"), sampleItems());

            assertThrows(IllegalStateException.class, order::markPaid);
        }

        @Test
        @DisplayName("markShipped() transitions from PAID to SHIPPED")
        void mark_shipped_from_paid() {
            Order order = Order.create(new OrderId("ORD-001"), new CustomerId("CUST-001"), sampleItems())
                    .markPaymentPending()
                    .markPaid();

            Order updated = order.markShipped();

            assertEquals(OrderStatus.SHIPPED, updated.status());
        }

        @Test
        @DisplayName("markShipped() fails from non-PAID status")
        void mark_shipped_fails_from_non_paid() {
            Order order = Order.create(new OrderId("ORD-001"), new CustomerId("CUST-001"), sampleItems())
                    .markPaymentPending();

            assertThrows(IllegalStateException.class, order::markShipped);
        }
    }

    // ── Order.totalAmount() ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Order.totalAmount()")
    class TotalAmountTests {

        @Test
        @DisplayName("calculates correctly across items")
        void calculates_correctly_across_items() {
            // PROD-1: 2 x $10.00 = $20.00
            // PROD-2: 1 x $25.50 = $25.50
            // Total = $45.50
            Order order = Order.create(new OrderId("ORD-001"), new CustomerId("CUST-001"), sampleItems());

            Money total = order.totalAmount();

            assertEquals(new BigDecimal("45.50"), total.amount());
            assertEquals("USD", total.currency());
        }
    }

    // ── OrderItem ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("OrderItem")
    class OrderItemTests {

        @Test
        @DisplayName("validates quantity >= 1")
        void validates_quantity_at_least_one() {
            assertThrows(IllegalArgumentException.class, () ->
                    new OrderItem(new ProductId("PROD-1"), 0, usd("10.00")));
        }

        @Test
        @DisplayName("validates non-null productId")
        void validates_non_null_product_id() {
            assertThrows(IllegalArgumentException.class, () ->
                    new OrderItem(null, 1, usd("10.00")));
        }

        @Test
        @DisplayName("validates non-null unitPrice")
        void validates_non_null_unit_price() {
            assertThrows(IllegalArgumentException.class, () ->
                    new OrderItem(new ProductId("PROD-1"), 1, null));
        }

        @Test
        @DisplayName("lineTotal() returns unitPrice * quantity")
        void line_total_returns_unit_price_times_quantity() {
            OrderItem item = new OrderItem(new ProductId("PROD-1"), 3, usd("10.00"));

            Money lineTotal = item.lineTotal();

            assertEquals(new BigDecimal("30.00"), lineTotal.amount());
            assertEquals("USD", lineTotal.currency());
        }
    }

    // ── Money ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Money")
    class MoneyTests {

        @Test
        @DisplayName("validates non-null amount")
        void validates_non_null_amount() {
            assertThrows(IllegalArgumentException.class, () ->
                    new Money(null, "USD"));
        }

        @Test
        @DisplayName("validates non-negative amount")
        void validates_non_negative_amount() {
            assertThrows(IllegalArgumentException.class, () ->
                    new Money(new BigDecimal("-1.00"), "USD"));
        }

        @Test
        @DisplayName("validates non-null currency")
        void validates_non_null_currency() {
            assertThrows(IllegalArgumentException.class, () ->
                    new Money(new BigDecimal("10.00"), null));
        }

        @Test
        @DisplayName("validates 3-char currency code")
        void validates_three_char_currency() {
            assertThrows(IllegalArgumentException.class, () ->
                    new Money(new BigDecimal("10.00"), "US"));
            assertThrows(IllegalArgumentException.class, () ->
                    new Money(new BigDecimal("10.00"), "USDD"));
        }

        @Test
        @DisplayName("currency is stored as uppercase")
        void currency_stored_as_uppercase() {
            Money money = new Money(new BigDecimal("10.00"), "usd");
            assertEquals("USD", money.currency());
        }

        @Test
        @DisplayName("add() adds same currency")
        void add_same_currency() {
            Money a = usd("10.00");
            Money b = usd("25.50");

            Money result = a.add(b);

            assertEquals(new BigDecimal("35.50"), result.amount());
            assertEquals("USD", result.currency());
        }

        @Test
        @DisplayName("add() fails on different currencies")
        void add_fails_on_different_currencies() {
            Money usd = new Money(new BigDecimal("10.00"), "USD");
            Money eur = new Money(new BigDecimal("10.00"), "EUR");

            assertThrows(IllegalArgumentException.class, () -> usd.add(eur));
        }

        @Test
        @DisplayName("multiply() multiplies by quantity")
        void multiply_by_quantity() {
            Money money = usd("10.00");

            Money result = money.multiply(3);

            assertEquals(new BigDecimal("30.00"), result.amount());
            assertEquals("USD", result.currency());
        }
    }

    // ── OrderId / CustomerId ─────────────────────────────────────────────────

    @Nested
    @DisplayName("OrderId / CustomerId / ProductId")
    class IdValueObjectTests {

        @Test
        @DisplayName("OrderId validates non-blank")
        void order_id_validates_non_blank() {
            assertThrows(IllegalArgumentException.class, () -> new OrderId(null));
            assertThrows(IllegalArgumentException.class, () -> new OrderId(""));
            assertThrows(IllegalArgumentException.class, () -> new OrderId("   "));
        }

        @Test
        @DisplayName("CustomerId validates non-blank")
        void customer_id_validates_non_blank() {
            assertThrows(IllegalArgumentException.class, () -> new CustomerId(null));
            assertThrows(IllegalArgumentException.class, () -> new CustomerId(""));
            assertThrows(IllegalArgumentException.class, () -> new CustomerId("   "));
        }

        @Test
        @DisplayName("ProductId validates non-blank")
        void product_id_validates_non_blank() {
            assertThrows(IllegalArgumentException.class, () -> new ProductId(null));
            assertThrows(IllegalArgumentException.class, () -> new ProductId(""));
            assertThrows(IllegalArgumentException.class, () -> new ProductId("   "));
        }
    }

    // ── OrderStatus ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("OrderStatus enum")
    class OrderStatusTests {

        @Test
        @DisplayName("has all expected values")
        void has_all_expected_values() {
            OrderStatus[] values = OrderStatus.values();
            assertEquals(4, values.length);
            assertEquals(OrderStatus.CREATED, OrderStatus.valueOf("CREATED"));
            assertEquals(OrderStatus.PAYMENT_PENDING, OrderStatus.valueOf("PAYMENT_PENDING"));
            assertEquals(OrderStatus.PAID, OrderStatus.valueOf("PAID"));
            assertEquals(OrderStatus.SHIPPED, OrderStatus.valueOf("SHIPPED"));
        }
    }
}
