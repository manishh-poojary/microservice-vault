package com.toolkit.microservices.vault.resilience.circuitbreaker;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * InventoryClient
 * -----------------
 *
 * @CircuitBreaker wraps this method with Resilience4j's circuit breaker,
 * configured under resilience4j.circuitbreaker.instances.inventoryService.
 * <p>
 * Same self-invocation caveat as @Retry applies here: this method must be
 * called through the Spring proxy (i.e. from another bean), or the
 * annotation is silently ignored.
 *
 * <p>
 * WHEN COMBINING WITH @Retry:
 * @CircuitBreaker(name = "inventoryService")
 * @Retry(name = "inventoryService")
 * public int checkStock(...) { ... }
 * <p>
 * <p>
 * Resilience4j applies annotations OUTSIDE-IN by default in the order:
 * CircuitBreaker -> RateLimiter -> Retry -> ... (innermost executes first
 * per call). This means Retry re-attempts happen INSIDE a single circuit
 * breaker-tracked call, so the breaker sees one pass/fail outcome for the
 * whole retried operation, not one per individual attempt.
 */
@Service
public class InventoryClient {

    private final RestTemplate restTemplate;

    public InventoryClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @CircuitBreaker(name = "inventoryService", fallbackMethod = "fallbackGetStock")
    public Integer getStock(String productId) {
        System.out.println("Calling Inventory Service for product: " + productId);
        String url = "http://inventory-service/api/stock/" + productId;
        return restTemplate.getForObject(url, Integer.class);
    }

    private Integer fallbackGetStock(String productId, Throwable ex) {
        System.out.println("Circuit breaker triggered for product " + productId
                + ". Reason: " + ex.getMessage());
        System.out.println("Returning fallback value (0 stock, treated as 'unknown/unavailable').");
        return -1; // safe default - caller can decide how to handle "0 or unknown"
    }
}
