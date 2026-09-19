package com.toolkit.microservices.vault.datamanagement.cqrs;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * OrderQueryService (READ side)
 * -----------------------------------
 * Queries ONLY the read model - never touches the write repository or
 * write entities at all. This separation is what lets the read model
 * evolve (new fields, different shape, even a different underlying
 * database) without ever touching command-side code, and vice versa.
 * <p>
 * No business logic here, no validation - reads should be as dumb and
 * fast as possible. Every field returned was already computed at WRITE
 * time by OrderSummaryProjector, not recalculated per query.
 */
@Service
public class OrderQueryService {

    private final OrderSummaryReadRepository summaryRepository;

    public OrderQueryService(OrderSummaryReadRepository summaryRepository) {
        this.summaryRepository = summaryRepository;
    }

    public Optional<OrderSummaryView> getOrderSummary(String orderId) {
        return summaryRepository.findById(orderId);
        // Empty means either "doesn't exist" OR "exists but hasn't been
        // projected yet" (eventual consistency window) - the CALLER
        // decides how to handle that ambiguity: a 404, a "processing"
        // status, or a client-side retry/poll. This example's controller
        // returns 404 either way, which is a simplification worth
        // reconsidering in a real UI (see the controller's comment).
    }

    public record OrderSummaryView(String orderId, String customerName, String productName,
                                   int quantity, BigDecimal totalPrice, String status) {
    }

    public interface OrderSummaryReadRepository {
        Optional<OrderSummaryView> findById(String orderId);
        // Swap the implementation of this interface to change storage
        // technology for reads ONLY - e.g. backed by a native SQL query
        // against a denormalized table, MongoDB, or even an in-memory
        // cache - without the command side ever knowing or caring.
    }
}