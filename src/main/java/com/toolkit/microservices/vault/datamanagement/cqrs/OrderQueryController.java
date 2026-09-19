package com.toolkit.microservices.vault.datamanagement.cqrs;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OrderQueryController
 * -----------------------
 * The READ entry point. Talks ONLY to OrderQueryService - never to
 * anything in the command/ package.
 * <p>
 * WORTH SITTING WITH: if a client calls POST /api/orders (place the
 * order) and IMMEDIATELY calls GET /api/orders/{id} (this endpoint), it
 * can get a 404 for an order that was just successfully created. That's
 * not a bug - it's the eventual consistency window made visible at the
 * API layer. A real UI usually handles this by either (a) having the
 * place-order response carry enough data to render the confirmation
 * screen WITHOUT needing to re-fetch, or (b) briefly retrying/polling
 * this endpoint client-side for a second or two after a create.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderQueryController {

    private final OrderQueryService queryService;

    public OrderQueryController(OrderQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderQueryService.OrderSummaryView> getOrder(@PathVariable String orderId) {
        return queryService.getOrderSummary(orderId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
