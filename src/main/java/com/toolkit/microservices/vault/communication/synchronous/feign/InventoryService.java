package com.toolkit.microservices.vault.communication.synchronous.feign;

import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Service;

/**
 * InventoryService
 * ------------------
 * Shows how a Feign client and Resilience4j annotations compose: this
 *
 * @Service method wraps a call to the generated Feign client with
 * @Retry, exactly like we'd wrap a RestTemplate call in earlier examples.
 * Feign is just the HTTP transport - it doesn't replace the resilience
 * layer, it sits underneath it.
 */
@Service
public class InventoryService {

    private final InventoryFeignClient inventoryFeignClient;

    public InventoryService(InventoryFeignClient inventoryFeignClient) {
        this.inventoryFeignClient = inventoryFeignClient;
    }

    @Retry(name = "inventoryService")
    public int getStock(String productId) {
        return inventoryFeignClient.checkStock(productId);
        // If this throws ServiceUnavailableException -> @Retry retries it.
        // If this throws ProductNotFoundException -> @Retry does NOT retry
        // (configured in ignore-exceptions in application.yml) - it fails
        // fast because retrying a 404 can never succeed.
        // If ALL retries are exhausted, or the circuit is OPEN (feign.circuitbreaker.enabled),
        // InventoryFeignClientFallback.checkStock() ultimately provides the degraded response.
    }
}
