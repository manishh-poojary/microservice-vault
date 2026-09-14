package com.toolkit.microservices.vault.communication.asynchronous.rabbitmq;

import com.toolkit.microservices.vault.communication.asynchronous.OrderCreatedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * OrderEventProducer
 * ---------------------
 * Compare directly to the Kafka version: same OrderCreatedEvent record
 * reused unchanged (the event PAYLOAD design - Event-Carried State
 * Transfer vs Event Notification - is a messaging-technology-agnostic
 * decision, not specific to Kafka or RabbitMQ).
 * <p>
 * KEY DIFFERENCE FROM KAFKA: you publish to an EXCHANGE with a ROUTING
 * KEY, not directly to a queue/topic. RabbitMQ's routing logic (defined
 * in RabbitConfig's bindings) decides which queue(s) actually receive it.
 */
@Service
public class OrderEventProducer {

    private final RabbitTemplate rabbitTemplate;

    public OrderEventProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishOrderCreated(OrderCreatedEvent event) {
        // convertAndSend is SYNCHRONOUS up to the point of handing the
        // message to the broker connection (unlike KafkaTemplate.send()
        // which returns a CompletableFuture) - RabbitTemplate does have
        // an async variant (via CorrelationData + publisher confirms) for
        // higher-throughput scenarios, but the simple form shown here is
        // the common default and is fine for most use cases.
        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE,
                RabbitConfig.ORDER_CREATED_ROUTING_KEY,
                event
        );
        System.out.println("Order event published for " + event.orderId()
                + " (routing key: " + RabbitConfig.ORDER_CREATED_ROUTING_KEY + ")");
        // If setReturnsCallback fired (see RabbitConfig), it means this
        // message reached the broker but couldn't be ROUTED anywhere -
        // that callback runs asynchronously shortly after this call returns.
    }
}