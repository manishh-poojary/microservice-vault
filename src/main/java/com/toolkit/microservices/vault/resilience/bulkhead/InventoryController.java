package com.toolkit.microservices.vault.resilience.bulkhead;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InventoryController {

    private final InventoryClient inventoryClient;

    public InventoryController(InventoryClient inventoryClient) {
        this.inventoryClient = inventoryClient;
    }

    @GetMapping("/stock/{productId}")
    public String getStockInfo(@PathVariable String productId) {
        return inventoryClient.getStockInfo(productId);
    }
}
