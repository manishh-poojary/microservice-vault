package com.toolkit.microservices.vault.datamanagement.cqrs;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * OrderCommandService (WRITE side)
 * ------------------------------------
 * Owns the normalized write model. Publishes domain events via Spring's
 * own ApplicationEventPublisher - no Kafka, no separate broker. Since
 * publisher and listener live in the SAME JVM, this is fundamentally
 * simpler than the Level 2 (Kafka) version, at the cost of not being
 * usable across service boundaries.
 *
 * IMPORTANT DEFAULT BEHAVIOR: ApplicationEventPublisher.publishEvent()
 * is SYNCHRONOUS by default - listeners run on the SAME thread, before
 * publishEvent() returns. That would defeat the purpose here (it'd just
 * be a normal method call with extra steps). The listener side
 * (OrderSummaryProjector) is marked @Async specifically to make this
 * genuinely asynchronous - see that class for why.
 */
@Service
public class OrderCommandService {

    private final OrderWriteRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrderCommandService(OrderWriteRepository orderRepository,
                               ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public String placeOrder(String customerId, String customerName,
                             String productId, String productName, int quantity, BigDecimal unitPrice) {
        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8);

        OrderEntity order = new OrderEntity(orderId, customerId, customerName, "PLACED");
        order.addItem(new OrderItemEntity(productId, productName, quantity, unitPrice));
        orderRepository.save(order); // LOCAL TRANSACTION on the normalized write model

        // NOTE: publishing INSIDE the @Transactional method means this
        // fires before the transaction commits, unless using
        // @TransactionalEventListener(phase = AFTER_COMMIT) on the
        // listener side - otherwise a listener could react to an event
        // for data that then fails to commit (rolls back). See the note
        // in OrderSummaryProjector.
        eventPublisher.publishEvent(new OrderCreatedEvent(
                orderId, customerId, customerName, productId, productName, quantity, unitPrice));

        return orderId;
    }

    @Transactional
    public void ship(String orderId, String trackingNumber) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        order.setStatus("SHIPPED");
        orderRepository.save(order);

        eventPublisher.publishEvent(new OrderStatusChangedEvent(orderId, "SHIPPED", trackingNumber));
    }

    // Events are simple nested records here - no shared "events" module
    // needed since publisher and listener are in the same codebase.
    public record OrderCreatedEvent(String orderId, String customerId, String customerName,
                                    String productId, String productName, int quantity, BigDecimal unitPrice) {}

    public record OrderStatusChangedEvent(String orderId, String newStatus, String trackingNumber) {}
}