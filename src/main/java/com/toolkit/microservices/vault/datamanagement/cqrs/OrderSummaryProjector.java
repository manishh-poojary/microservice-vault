package com.toolkit.microservices.vault.datamanagement.cqrs;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;

/**
 * OrderSummaryProjector
 * -------------------------
 * Reacts to command-side events and updates the denormalized read model.
 * This is THE piece that keeps the query side eventually consistent.
 * <p>
 * TWO IMPORTANT ANNOTATIONS HERE, BOTH DELIBERATE:
 *
 * @TransactionalEventListener(phase = AFTER_COMMIT) instead of plain
 * @EventListener: this listener only fires AFTER the write-side
 * transaction successfully commits. Without this, a listener using plain
 * @EventListener could react to an event for a write that later ROLLS
 * BACK (e.g. a later step in the same @Transactional method throws) -
 * you'd have a read model reflecting data that was never actually saved.
 * @Async: runs this on a separate thread from the command that triggered
 * it. Combined with AFTER_COMMIT, this means: the command's transaction
 * commits, the HTTP response returns to the client, and ONLY THEN does
 * this method run on a background thread. This is what creates the
 * (small, in-process) eventual consistency window - genuinely the same
 * phenomenon as the Kafka-based Level 2 version, just with far lower
 * latency since there's no network/broker hop involved.
 * <p>
 * Requires @EnableAsync on a @Configuration class elsewhere in the app.
 */
@Component
public class OrderSummaryProjector {

    private final OrderSummaryWriteRepository readModelRepository;

    public OrderSummaryProjector(OrderSummaryWriteRepository readModelRepository) {
        this.readModelRepository = readModelRepository;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderCommandService.OrderCreatedEvent event) {
        // PRE-COMPUTE derived fields HERE, at write time, not read time -
        // this is the actual performance payoff of denormalization:
        // totalPrice is stored ready-to-serve, never recalculated per read.
        BigDecimal totalPrice = event.unitPrice().multiply(BigDecimal.valueOf(event.quantity()));

        readModelRepository.save(new OrderQueryService.OrderSummaryView(
                event.orderId(), event.customerName(), event.productName(),
                event.quantity(), totalPrice, "PLACED"));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderCommandService.OrderStatusChangedEvent event) {
        readModelRepository.updateStatus(event.orderId(), event.newStatus());
    }

    public interface OrderSummaryWriteRepository {
        void save(OrderQueryService.OrderSummaryView view);

        void updateStatus(String orderId, String newStatus);
    }
}