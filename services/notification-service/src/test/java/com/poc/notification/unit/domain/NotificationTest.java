package com.poc.notification.unit.domain;

import com.poc.notification.domain.model.Notification;
import com.poc.notification.domain.model.NotificationId;
import com.poc.notification.domain.model.NotificationStatus;
import com.poc.notification.domain.model.NotificationType;
import com.poc.notification.domain.model.OrderId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NotificationTest {

    // ── Fixtures ─────────────────────────────────────────────────────────────────

    private static final NotificationId VALID_NOTIFICATION_ID = new NotificationId("NOTIF-001");
    private static final OrderId VALID_ORDER_ID = new OrderId("ORD-001");
    private static final NotificationType VALID_TYPE = NotificationType.PAYMENT_CONFIRMED;
    private static final String VALID_MESSAGE = "Payment confirmed for order ORD-001";

    private static Notification validNotification() {
        return Notification.create(VALID_NOTIFICATION_ID, VALID_ORDER_ID, VALID_TYPE, VALID_MESSAGE);
    }

    // ── Notification.create() ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Notification.create()")
    class Create {

        @Test
        @DisplayName("creates notification in PENDING status with correct fields")
        void creates_notification_in_pending_status() {
            Notification notification = validNotification();

            assertEquals(VALID_NOTIFICATION_ID, notification.notificationId());
            assertEquals(VALID_ORDER_ID, notification.orderId());
            assertEquals(VALID_TYPE, notification.type());
            assertEquals(NotificationStatus.PENDING, notification.status());
            assertEquals(VALID_MESSAGE, notification.message());
            assertNotNull(notification.createdAt());
        }

        @Test
        @DisplayName("fails with null notificationId")
        void fails_with_null_notification_id() {
            assertThrows(IllegalArgumentException.class, () ->
                    Notification.create(null, VALID_ORDER_ID, VALID_TYPE, VALID_MESSAGE));
        }

        @Test
        @DisplayName("fails with null orderId")
        void fails_with_null_order_id() {
            assertThrows(IllegalArgumentException.class, () ->
                    Notification.create(VALID_NOTIFICATION_ID, null, VALID_TYPE, VALID_MESSAGE));
        }

        @Test
        @DisplayName("fails with null type")
        void fails_with_null_type() {
            assertThrows(IllegalArgumentException.class, () ->
                    Notification.create(VALID_NOTIFICATION_ID, VALID_ORDER_ID, null, VALID_MESSAGE));
        }

        @Test
        @DisplayName("fails with null message")
        void fails_with_null_message() {
            assertThrows(IllegalArgumentException.class, () ->
                    Notification.create(VALID_NOTIFICATION_ID, VALID_ORDER_ID, VALID_TYPE, null));
        }

        @Test
        @DisplayName("fails with blank message")
        void fails_with_blank_message() {
            assertThrows(IllegalArgumentException.class, () ->
                    Notification.create(VALID_NOTIFICATION_ID, VALID_ORDER_ID, VALID_TYPE, ""));
            assertThrows(IllegalArgumentException.class, () ->
                    Notification.create(VALID_NOTIFICATION_ID, VALID_ORDER_ID, VALID_TYPE, "   "));
        }
    }

    // ── Notification.markSent() ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Notification.markSent()")
    class MarkSent {

        @Test
        @DisplayName("transitions from PENDING to SENT")
        void transitions_from_pending_to_sent() {
            Notification pending = validNotification();

            Notification sent = pending.markSent();

            assertEquals(NotificationStatus.SENT, sent.status());
            assertEquals(pending.notificationId(), sent.notificationId());
            assertEquals(pending.orderId(), sent.orderId());
            assertEquals(pending.type(), sent.type());
            assertEquals(pending.message(), sent.message());
            assertEquals(pending.createdAt(), sent.createdAt());
        }

        @Test
        @DisplayName("fails from SENT status")
        void fails_from_sent_status() {
            Notification sent = validNotification().markSent();

            assertThrows(IllegalStateException.class, sent::markSent);
        }

        @Test
        @DisplayName("fails from FAILED status")
        void fails_from_failed_status() {
            Notification failed = validNotification().markFailed();

            assertThrows(IllegalStateException.class, failed::markSent);
        }
    }

    // ── Notification.markFailed() ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Notification.markFailed()")
    class MarkFailed {

        @Test
        @DisplayName("transitions from PENDING to FAILED")
        void transitions_from_pending_to_failed() {
            Notification pending = validNotification();

            Notification failed = pending.markFailed();

            assertEquals(NotificationStatus.FAILED, failed.status());
            assertEquals(pending.notificationId(), failed.notificationId());
            assertEquals(pending.orderId(), failed.orderId());
            assertEquals(pending.type(), failed.type());
            assertEquals(pending.message(), failed.message());
            assertEquals(pending.createdAt(), failed.createdAt());
        }

        @Test
        @DisplayName("fails from SENT status")
        void fails_from_sent_status() {
            Notification sent = validNotification().markSent();

            assertThrows(IllegalStateException.class, sent::markFailed);
        }

        @Test
        @DisplayName("fails from FAILED status")
        void fails_from_failed_status() {
            Notification failed = validNotification().markFailed();

            assertThrows(IllegalStateException.class, failed::markFailed);
        }
    }

    // ── Record validation ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Record validation")
    class RecordValidation {

        @Test
        @DisplayName("fails with null status")
        void fails_with_null_status() {
            assertThrows(IllegalArgumentException.class, () ->
                    new Notification(VALID_NOTIFICATION_ID, VALID_ORDER_ID, VALID_TYPE,
                            null, VALID_MESSAGE, Instant.now()));
        }

        @Test
        @DisplayName("fails with null createdAt")
        void fails_with_null_created_at() {
            assertThrows(IllegalArgumentException.class, () ->
                    new Notification(VALID_NOTIFICATION_ID, VALID_ORDER_ID, VALID_TYPE,
                            NotificationStatus.PENDING, VALID_MESSAGE, null));
        }
    }

    // ── NotificationId ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("NotificationId")
    class NotificationIdTest {

        @Test
        @DisplayName("validates non-blank id")
        void validates_non_blank_id() {
            assertThrows(IllegalArgumentException.class, () -> new NotificationId(null));
            assertThrows(IllegalArgumentException.class, () -> new NotificationId(""));
            assertThrows(IllegalArgumentException.class, () -> new NotificationId("   "));
        }
    }

    // ── NotificationType ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("NotificationType")
    class NotificationTypeTest {

        @Test
        @DisplayName("has PAYMENT_CONFIRMED and SHIPPING_ARRANGED values")
        void has_expected_values() {
            NotificationType[] values = NotificationType.values();

            assertEquals(2, values.length);
            assertEquals(NotificationType.PAYMENT_CONFIRMED, NotificationType.valueOf("PAYMENT_CONFIRMED"));
            assertEquals(NotificationType.SHIPPING_ARRANGED, NotificationType.valueOf("SHIPPING_ARRANGED"));
        }
    }

    // ── NotificationStatus ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("NotificationStatus")
    class NotificationStatusTest {

        @Test
        @DisplayName("has PENDING, SENT, FAILED values")
        void has_expected_values() {
            NotificationStatus[] values = NotificationStatus.values();

            assertEquals(3, values.length);
            assertEquals(NotificationStatus.PENDING, NotificationStatus.valueOf("PENDING"));
            assertEquals(NotificationStatus.SENT, NotificationStatus.valueOf("SENT"));
            assertEquals(NotificationStatus.FAILED, NotificationStatus.valueOf("FAILED"));
        }
    }

    // ── OrderId ───────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("OrderId")
    class OrderIdTest {

        @Test
        @DisplayName("validates non-blank id")
        void validates_non_blank_id() {
            assertThrows(IllegalArgumentException.class, () -> new OrderId(null));
            assertThrows(IllegalArgumentException.class, () -> new OrderId(""));
            assertThrows(IllegalArgumentException.class, () -> new OrderId("   "));
        }
    }
}
