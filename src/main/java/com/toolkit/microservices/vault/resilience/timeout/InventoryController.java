package com.toolkit.microservices.vault.resilience.timeout;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ExecutionException;

@RestController
public class InventoryController {

    private final InventoryClient inventoryClient;

    public InventoryController(InventoryClient inventoryClient) {
        this.inventoryClient = inventoryClient;
    }

    @GetMapping("/stock/{productId}")
    public Integer getStockInfo(String productId) throws ExecutionException, InterruptedException {
        // Simulate a call to the InventoryClient which may have a timeout
        return inventoryClient.checkStock(productId).get();
    }
}
