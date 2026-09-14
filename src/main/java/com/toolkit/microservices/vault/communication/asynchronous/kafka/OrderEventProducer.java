package com.toolkit.microservices.vault.communication.asynchronous.kafka;

import com.toolkit.microservices.vault.communication.asynchronous.OrderCreatedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * OrderEventProducer
 * ---------------------
 * Publishes events asynchronously - this is the "fire and publish, don't
 * wait for a response" flavor of async communication, genuinely different
 * from WebClient's non-blocking-but-still-request/response model covered
 * in the previous topic.
 * <p>
 * KafkaTemplate.send() returns a CompletableFuture<SendResult<K,V>> -
 * completing when the message is ACKNOWLEDGED BY KAFKA (durably written,
 * per the 'acks' setting), NOT when any consumer has processed it. The
 * producer has NO knowledge of, or dependency on, which services (if any)
 * are currently consuming this topic - that's the core decoupling benefit
 * of event-driven communication.
 */
@Service
public class OrderEventProducer {

    private static final String TOPIC = "order-created-events";

    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    public OrderEventProducer(KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Using orderId as the partition KEY - Kafka guarantees all
     * messages with the SAME key go to the SAME partition, and
     * messages WITHIN a partition are strictly ordered. This means
     * events for the same order are always processed in order by any
     * single consumer, even though different orders may be processed
     * out of order relative to each other across partitions.
     */
    public void publishOrderCreated(OrderCreatedEvent event) {

        CompletableFuture<SendResult<String, OrderCreatedEvent>> future =
                kafkaTemplate.send(TOPIC, event.orderId(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                System.out.println("Failed to publish order event for " + event.orderId() + ": " + ex.getMessage());
                // In production: alert/metric here. The order was likely
                // already persisted to the DB by this point (see the
                // Saga/outbox note below) - a failed PUBLISH doesn't mean
                // the order itself failed, it means downstream services
                // won't find out about it via this path, which needs its
                // own handling (retry the publish, outbox pattern, etc.)
            } else {
                System.out.println("Order event published for " + event.orderId()
                        + " to partition " + result.getRecordMetadata().partition()
                        + " at offset " + result.getRecordMetadata().offset());
            }
        });

        // NOTE: this method returns immediately - it does NOT wait for
        // the future to complete. That's the "fire and forget" nature of
        // this style. If you needed to block until Kafka acknowledges
        // (rare, but sometimes wanted at the edge of a synchronous API),
        // you'd call future.get() - which reintroduces blocking, so avoid
        // unless you have a specific reason.
    }
}
