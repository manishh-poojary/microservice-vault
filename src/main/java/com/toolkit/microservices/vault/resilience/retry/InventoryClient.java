package com.toolkit.microservices.vault.resilience.retry;

import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * InventoryClient
 * -----------------
 * Calls a downstream "Inventory" microservice. The @Retry annotation
 * wraps this method with Resilience4j's retry logic, configured in
 * application.yml under resilience4j.retry.instances.inventoryService.
 *
 * IMPORTANT: @Retry (and other Resilience4j annotations) work via Spring
 * AOP proxies. This means:
 *   1. The method must be called from ANOTHER Spring bean (not from within
 *      the same class via 'this.checkStock()') - self-invocation bypasses
 *      the proxy and the annotation is silently ignored.
 *   2. The class must be a Spring-managed bean (@Service, @Component, etc.)
 */
@Service
public class InventoryClient {

    private final RestTemplate restTemplate;

    public InventoryClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * fallbackMethod is called ONLY after all retry attempts are exhausted.
     * Its signature must match the original method + accept the exception
     * type as the last parameter.
     */
    @Retry(name = "inventoryService", fallbackMethod = "fallbackGetStock")
    public Integer getStock(String productId) {
        System.out.println("Calling Inventory Service for product: " + productId);

        // Simulated downstream call - in reality this hits another microservice
        String url = "http://inventory-service/api/stock/" + productId;
        return restTemplate.getForObject(url, Integer.class);
    }

    /**
     * Fallback: called after retries are exhausted (or on a non-retryable exception
     * if you don't separately handle it). Keeps the caller from seeing a raw exception.
     */
    private Integer fallbackGetStock(String productId, ResourceAccessException ex) {
        System.out.println("All retries exhausted for product " + productId
                + ". Reason: " + ex.getMessage());
        System.out.println("Returning fallback value (0 stock, treated as 'unknown/unavailable').");
        return -1; // safe default - caller can decide how to handle "0 or unknown"
    }
}
