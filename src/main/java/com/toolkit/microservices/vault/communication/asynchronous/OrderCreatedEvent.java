package com.toolkit.microservices.vault.communication.asynchronous;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * OrderCreatedEvent
 * --------------------
 * This is an EVENT-CARRIED STATE TRANSFER style event - it carries enough
 * data (items, total, customer) that a consumer can act on it WITHOUT
 * needing to call back to the Order Service to fetch more details.
 * <p>
 * COMPARE TO "Event Notification" style (the other common approach):
 * record OrderCreatedNotification(String orderId) {}
 * - minimal payload, just "something happened, here's the ID"
 * - consumers that need more detail must call back to Order Service's
 * API to fetch it
 * <p>
 * TRADE-OFF:
 * - Event-Carried State Transfer: consumers are decoupled from Order
 * Service's availability (no callback needed), but the event schema
 * is heavier and changes to what data consumers need means changing
 * the event contract, affecting every consumer.
 * - Event Notification: lightweight events, easy to evolve, but
 * consumers become coupled to Order Service's uptime for the callback -
 * which partially defeats the resilience benefit of going async in
 * the first place if Order Service is down when the callback happens.
 * <p>
 * Most real systems use Event-Carried State Transfer for the common case
 * (as below) and fall back to Event Notification + callback only for
 * rarely-needed, large, or highly volatile associated data.
 */
public record OrderCreatedEvent(
        String orderId,
        String customerId,
        List<OrderItem> items,
        BigDecimal totalAmount,
        Instant createdAt
) {
    public record OrderItem(String productId, int quantity, BigDecimal unitPrice) {
    }
}
