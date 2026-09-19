package com.toolkit.microservices.vault.datamanagement.cqrs;

import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * OrderCommandController
 * -------------------------
 * The WRITE entry point. Note this is a completely separate controller
 * from OrderQueryController (in the query/ package) - two different
 * classes, two different responsibilities. That split is literally what
 * "Command Query Responsibility SEGREGATION" refers to.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderCommandController {

    private final OrderCommandService commandService;

    public OrderCommandController(OrderCommandService commandService) {
        this.commandService = commandService;
    }

    public record PlaceOrderRequest(String customerId, String customerName,
                                    String productId, String productName,
                                    int quantity, BigDecimal unitPrice) {
    }

    public record ShipRequest(String trackingNumber) {
    }

    @PostMapping
    public String placeOrder(@RequestBody PlaceOrderRequest req) {
        return commandService.placeOrder(req.customerId(), req.customerName(),
                req.productId(), req.productName(), req.quantity(), req.unitPrice());
        // Returns as soon as the WRITE model is saved. The read model
        // catches up moments later, on a background thread - see
        // OrderQueryController for what that means for a caller.
    }

    @PostMapping("/{orderId}/ship")
    public void ship(@PathVariable String orderId, @RequestBody ShipRequest req) {
        commandService.ship(orderId, req.trackingNumber());
    }
}