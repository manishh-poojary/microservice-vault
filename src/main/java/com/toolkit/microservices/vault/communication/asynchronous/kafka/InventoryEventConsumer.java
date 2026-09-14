package com.toolkit.microservices.vault.communication.asynchronous.kafka;

import com.toolkit.microservices.vault.communication.asynchronous.OrderCreatedEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

/**
 * InventoryEventConsumer
 * --------------------------
 * Listens for OrderCreatedEvent and reacts by decrementing stock -
 * completely decoupled from the Order Service. Order Service doesn't know
 * this consumer exists, doesn't wait for it, and would work identically
 * if this consumer were down. (The event would just wait in Kafka until
 * this service comes back and catches up - THIS is the real resilience
 * benefit of event-driven communication over synchronous calls.)
 * <p>
 * COMPETING CONSUMERS PATTERN:
 * If you run 3 instances of Inventory Service, all with the SAME
 * group-id ("inventory-service", set in application.yml), Kafka
 * automatically splits the topic's partitions across them - each
 * message is delivered to exactly ONE instance, not all three. This
 * gives you horizontal scalability for free: more instances = more
 * parallel processing capacity, up to the number of partitions.
 * <p>
 * (If instead you wanted EVERY instance to see EVERY message - e.g. for
 * cache invalidation broadcasts - each instance would need its OWN
 * unique group-id instead of sharing one.)
 */
@Service
public class InventoryEventConsumer {

    @KafkaListener(topics = "order-created-events", groupId = "inventory-service")
    public void onOrderCreated(ConsumerRecord<String, OrderCreatedEvent> record, Acknowledgment ack) {
        OrderCreatedEvent event = record.value();
        System.out.println("Processing order " + event.orderId() + " (partition "
                + record.partition() + ", offset " + record.offset() + ")");

        try {
            for (OrderCreatedEvent.OrderItem item : event.items()) {
                decrementStock(item.productId(), item.quantity());
            }

            // Manual acknowledgment (ack-mode: manual in application.yml) -
            // the offset is only committed AFTER processing succeeds. If
            // the service crashes mid-processing, the message will be
            // redelivered on restart (at-least-once delivery semantics) -
            // which is why decrementStock() below should be IDEMPOTENT.
            ack.acknowledge();
            System.out.println("Order " + event.orderId() + " processed and acknowledged.");

        } catch (Exception e) {
            System.out.println("Failed to process order " + event.orderId() + ": " + e.getMessage());
            // Do NOT acknowledge - message will be redelivered (subject to
            // the error handler/retry config in KafkaConfig.java, which
            // eventually routes persistently-failing messages to a DLQ
            // instead of retrying forever).
            throw e; // rethrow so the container's error handler takes over
        }
    }

    // IDEMPOTENCY MATTERS: because Kafka guarantees at-least-once delivery
    // by default (a message CAN be redelivered, e.g. after a crash before
    // ack, or a consumer rebalance), this method must be safe to call more
    // than once with the same event without double-decrementing stock.
    // Real implementations typically track "already processed" event IDs
    // (e.g. a processed_events table keyed by a unique event ID) or use
    // a compare-and-set style update rather than a blind decrement.
    private void decrementStock(String productId, int quantity) {
        System.out.println("  Decrementing stock for " + productId + " by " + quantity);
        // ... actual DB update logic (idempotent!) goes here
    }
}