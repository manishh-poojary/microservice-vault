package com.toolkit.microservices.vault.communication.asynchronous.rabbitmq;

import com.rabbitmq.client.Channel;
import com.toolkit.microservices.vault.communication.asynchronous.OrderCreatedEvent;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * InventoryEventConsumer
 * --------------------------
 * COMPETING CONSUMERS in RabbitMQ: unlike Kafka (where it's controlled by
 * sharing a consumer group-id), in RabbitMQ it's automatic and inherent -
 * MULTIPLE CONSUMERS BOUND TO THE SAME QUEUE simply compete for messages
 * round-robin by default. Run 3 instances of this service, all listening
 * to the same queue name, and RabbitMQ distributes messages across them
 * with zero extra configuration (no group-id concept needed at all).
 * <p>
 * If you wanted EVERY instance to see EVERY message instead (broadcast),
 * you'd give each instance its OWN queue, all bound to the SAME fanout
 * exchange - the routing topology, not a consumer setting, controls this
 * in RabbitMQ.
 */
@Service
public class InventoryEventConsumer {

    @RabbitListener(queues = "inventory.order-created.queue")
    public void onOrderCreated(OrderCreatedEvent event, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        System.out.println("Processing order " + event.orderId());

        try {
            for (OrderCreatedEvent.OrderItem item : event.items()) {
                decrementStock(item.productId(), item.quantity());
            }

            // Manual ack (acknowledge-mode: manual in application.yml) -
            // tells RabbitMQ this message was successfully processed and
            // can be permanently removed from the queue.
            channel.basicAck(deliveryTag, false);
            System.out.println("Order " + event.orderId() + " processed and acknowledged.");

        } catch (Exception e) {
            System.out.println("Failed to process order " + event.orderId() + ": " + e.getMessage());

            // basicNack with requeue=false -> RabbitMQ routes this message
            // to the Dead Letter Exchange configured on the queue (see
            // RabbitConfig's x-dead-letter-exchange argument) INSTEAD of
            // requeueing it for immediate redelivery. Combined with
            // spring.rabbitmq.listener.simple.retry in application.yml,
            // Spring actually handles local retries BEFORE you even reach
            // this catch block in most setups - this manual nack path is
            // what runs once those local retries are exhausted.
            channel.basicNack(deliveryTag, false, false);
        }
    }

    // Same idempotency requirement as the Kafka consumer - RabbitMQ's
    // default delivery guarantee is also at-least-once (a redelivery can
    // happen after a consumer crash before ack, for example).
    private void decrementStock(String productId, int quantity) {
        System.out.println("  Decrementing stock for " + productId + " by " + quantity);
    }
}
