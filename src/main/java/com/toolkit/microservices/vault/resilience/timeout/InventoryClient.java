package com.toolkit.microservices.vault.resilience.timeout;

import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

/**
 * InventoryClient
 * -----------------
 * @TimeLimiter is Resilience4j's Timeout pattern implementation.
 *
 * IMPORTANT CONSTRAINT: @TimeLimiter ONLY works on methods that return
 * CompletableFuture<T> (or another reactive/async type). It cannot time
 * out a plain synchronous/blocking method call - there's no safe way for
 * Resilience4j to forcibly interrupt a blocking call happening on the
 * calling thread itself. The method body needs to run on a SEPARATE
 * thread so it CAN be abandoned (the caller stops waiting on it) if it
 * runs too long.
 *
 * This is why the actual HTTP call below happens inside
 * CompletableFuture.supplyAsync() - that puts it on a background thread
 * that @TimeLimiter can walk away from.
 */
@Service
public class InventoryClient {

    private final RestTemplate restTemplate;

    public InventoryClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @TimeLimiter(name = "inventoryService", fallbackMethod = "checkStockFallback")
    public CompletableFuture<Integer> checkStock(String productId) {
        return CompletableFuture.supplyAsync(() -> {
            System.out.println("Calling Inventory Service for product: " + productId);
            String url = "http://inventory-service/api/stock/" + productId;
            return restTemplate.getForObject(url, Integer.class);
        });
        // Note: even with @TimeLimiter walking away from this Future, the
        // underlying RestTemplate call may keep running in the background
        // until ITS OWN read-timeout (configured in application.yml above)
        // fires. @TimeLimiter bounds how long the CALLER waits; the HTTP
        // client's own timeout bounds the actual network resource usage.
        // Configure BOTH for correct behavior.
    }

    // Fallback signature must accept Throwable as the last parameter -
    // TimeoutException specifically is thrown when the time limit is exceeded.
    private CompletableFuture<Integer> checkStockFallback(String productId, TimeoutException ex) {
        System.out.println("Stock check for " + productId + " timed out: " + ex.getMessage());
        return CompletableFuture.completedFuture(-1); // sentinel: "unknown, treat cautiously"
    }
}
