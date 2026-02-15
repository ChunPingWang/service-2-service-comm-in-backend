package com.poc.product.unit.domain;

import com.poc.product.domain.model.Category;
import com.poc.product.domain.model.Money;
import com.poc.product.domain.model.Product;
import com.poc.product.domain.model.ProductId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ProductTest {

    // ── Helper factories ─────────────────────────────────────────────────────────

    private static ProductId validProductId() {
        return new ProductId("PROD-001");
    }

    private static Money validPrice() {
        return new Money(new BigDecimal("29.99"), "USD");
    }

    // ── Product creation ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Product creation")
    class Creation {

        @Test
        @DisplayName("should create a valid product with all fields")
        void validProduct() {
            var product = new Product(
                    validProductId(),
                    "Wireless Mouse",
                    "Ergonomic wireless mouse",
                    validPrice(),
                    100,
                    Category.ELECTRONICS
            );

            assertEquals(validProductId(), product.productId());
            assertEquals("Wireless Mouse", product.name());
            assertEquals("Ergonomic wireless mouse", product.description());
            assertEquals(validPrice(), product.price());
            assertEquals(100, product.stockQuantity());
            assertEquals(Category.ELECTRONICS, product.category());
        }

        @Test
        @DisplayName("should fail with null productId")
        void nullProductId() {
            assertThrows(IllegalArgumentException.class, () ->
                    new Product(null, "Name", "Desc", validPrice(), 10, Category.ELECTRONICS));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "\t"})
        @DisplayName("should fail with blank name")
        void blankName(String name) {
            assertThrows(IllegalArgumentException.class, () ->
                    new Product(validProductId(), name, "Desc", validPrice(), 10, Category.ELECTRONICS));
        }

        @Test
        @DisplayName("should fail with null price")
        void nullPrice() {
            assertThrows(IllegalArgumentException.class, () ->
                    new Product(validProductId(), "Name", "Desc", null, 10, Category.ELECTRONICS));
        }

        @Test
        @DisplayName("should fail with negative stockQuantity")
        void negativeStock() {
            assertThrows(IllegalArgumentException.class, () ->
                    new Product(validProductId(), "Name", "Desc", validPrice(), -1, Category.ELECTRONICS));
        }

        @Test
        @DisplayName("should fail with null category")
        void nullCategory() {
            assertThrows(IllegalArgumentException.class, () ->
                    new Product(validProductId(), "Name", "Desc", validPrice(), 10, null));
        }

        @Test
        @DisplayName("should fail when price amount is zero")
        void zeroPriceAmount() {
            var zeroPrice = new Money(BigDecimal.ZERO, "USD");
            assertThrows(IllegalArgumentException.class, () ->
                    new Product(validProductId(), "Name", "Desc", zeroPrice, 10, Category.ELECTRONICS));
        }

        @Test
        @DisplayName("should fail when price amount is negative")
        void negativePriceAmount() {
            // Money itself rejects negative amounts at construction time,
            // so a Product can never be created with a negative price.
            // This test verifies that Money rejects negative amounts.
            assertThrows(IllegalArgumentException.class, () ->
                    new Money(new BigDecimal("-5.00"), "USD"));
        }

        @Test
        @DisplayName("should default description to empty string when null")
        void nullDescriptionDefaultsToEmpty() {
            var product = new Product(
                    validProductId(), "Name", null, validPrice(), 10, Category.ELECTRONICS);
            assertEquals("", product.description());
        }
    }

    // ── hasAvailableStock ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("hasAvailableStock")
    class HasAvailableStock {

        @Test
        @DisplayName("should return true when stock is sufficient")
        void sufficientStock() {
            var product = new Product(
                    validProductId(), "Item", "Desc", validPrice(), 10, Category.BOOKS);
            assertTrue(product.hasAvailableStock(10));
            assertTrue(product.hasAvailableStock(1));
        }

        @Test
        @DisplayName("should return false when stock is insufficient")
        void insufficientStock() {
            var product = new Product(
                    validProductId(), "Item", "Desc", validPrice(), 5, Category.BOOKS);
            assertFalse(product.hasAvailableStock(6));
        }

        @Test
        @DisplayName("should fail when quantity is less than 1")
        void quantityLessThanOne() {
            var product = new Product(
                    validProductId(), "Item", "Desc", validPrice(), 5, Category.BOOKS);
            assertThrows(IllegalArgumentException.class, () -> product.hasAvailableStock(0));
            assertThrows(IllegalArgumentException.class, () -> product.hasAvailableStock(-1));
        }
    }

    // ── reserveStock ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("reserveStock")
    class ReserveStock {

        @Test
        @DisplayName("should reduce stock by the requested quantity")
        void reducesStock() {
            var product = new Product(
                    validProductId(), "Item", "Desc", validPrice(), 10, Category.HOME);
            var updated = product.reserveStock(3);
            assertEquals(7, updated.stockQuantity());
            // Original must be unchanged (immutable record)
            assertEquals(10, product.stockQuantity());
        }

        @Test
        @DisplayName("should fail when insufficient stock")
        void insufficientStock() {
            var product = new Product(
                    validProductId(), "Item", "Desc", validPrice(), 2, Category.HOME);
            assertThrows(IllegalStateException.class, () -> product.reserveStock(3));
        }
    }

    // ── ProductId ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ProductId")
    class ProductIdTest {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "\t"})
        @DisplayName("should reject blank id")
        void rejectsBlankId(String id) {
            assertThrows(IllegalArgumentException.class, () -> new ProductId(id));
        }

        @Test
        @DisplayName("should accept valid id")
        void acceptsValidId() {
            var pid = new ProductId("PROD-001");
            assertEquals("PROD-001", pid.id());
        }
    }

    // ── Category ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Category")
    class CategoryTest {

        @Test
        @DisplayName("should have all expected values")
        void allExpectedValues() {
            var expected = new Category[]{
                    Category.ELECTRONICS,
                    Category.CLOTHING,
                    Category.BOOKS,
                    Category.HOME,
                    Category.SPORTS
            };
            assertArrayEquals(expected, Category.values());
        }

        @ParameterizedTest
        @ValueSource(strings = {"electronics", "Electronics", "ELECTRONICS", "eLeCTrOnIcS"})
        @DisplayName("fromString should perform case-insensitive lookup")
        void caseInsensitiveLookup(String input) {
            assertEquals(Category.ELECTRONICS, Category.fromString(input));
        }

        @Test
        @DisplayName("fromString should fail on unknown category")
        void unknownCategory() {
            assertThrows(IllegalArgumentException.class, () -> Category.fromString("UNKNOWN"));
        }
    }

    // ── Money ────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Money")
    class MoneyTest {

        @Test
        @DisplayName("should reject null amount")
        void nullAmount() {
            assertThrows(IllegalArgumentException.class, () -> new Money(null, "USD"));
        }

        @Test
        @DisplayName("should reject negative amount")
        void negativeAmount() {
            assertThrows(IllegalArgumentException.class, () ->
                    new Money(new BigDecimal("-1.00"), "USD"));
        }

        @Test
        @DisplayName("should reject null currency")
        void nullCurrency() {
            assertThrows(IllegalArgumentException.class, () ->
                    new Money(BigDecimal.TEN, null));
        }

        @ParameterizedTest
        @ValueSource(strings = {"US", "USDX", "", "ab"})
        @DisplayName("should reject currency that is not exactly 3 characters")
        void invalidCurrencyLength(String currency) {
            assertThrows(IllegalArgumentException.class, () ->
                    new Money(BigDecimal.TEN, currency));
        }

        @Test
        @DisplayName("should accept zero amount")
        void zeroAmount() {
            var money = new Money(BigDecimal.ZERO, "USD");
            assertEquals(BigDecimal.ZERO, money.amount());
            assertEquals("USD", money.currency());
        }

        @Test
        @DisplayName("add should sum two money values with same currency")
        void addSameCurrency() {
            var a = new Money(new BigDecimal("10.00"), "USD");
            var b = new Money(new BigDecimal("5.50"), "USD");
            var result = a.add(b);
            assertEquals(new BigDecimal("15.50"), result.amount());
            assertEquals("USD", result.currency());
        }

        @Test
        @DisplayName("add should fail with different currencies")
        void addDifferentCurrencies() {
            var usd = new Money(BigDecimal.TEN, "USD");
            var eur = new Money(BigDecimal.ONE, "EUR");
            assertThrows(IllegalArgumentException.class, () -> usd.add(eur));
        }

        @Test
        @DisplayName("multiply should scale money by integer quantity")
        void multiplyByQuantity() {
            var price = new Money(new BigDecimal("7.50"), "USD");
            var result = price.multiply(3);
            assertEquals(new BigDecimal("22.50"), result.amount());
            assertEquals("USD", result.currency());
        }

        @Test
        @DisplayName("multiply should fail with non-positive multiplier")
        void multiplyByZeroOrNegative() {
            var price = new Money(new BigDecimal("7.50"), "USD");
            assertThrows(IllegalArgumentException.class, () -> price.multiply(0));
            assertThrows(IllegalArgumentException.class, () -> price.multiply(-1));
        }
    }
}
