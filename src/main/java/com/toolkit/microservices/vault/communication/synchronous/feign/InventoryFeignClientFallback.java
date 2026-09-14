package com.toolkit.microservices.vault.communication.synchronous.feign;

import org.springframework.stereotype.Component;

/**
 * InventoryFeignClientFallback
 * --------------------------------
 * A plain Spring bean implementing the SAME interface as the Feign client.
 * Wired automatically via the `fallback` attribute on @FeignClient.
 * <p>
 * IMPORTANT LIMITATION: this fallback style gives you NO access to the
 * actual exception that caused the failure - just "something went wrong,
 * here's your degraded response." If you need the exception (e.g. to log
 * different messages for a timeout vs a 404 vs a connection refused), use
 * `fallbackFactory` instead of `fallback` (see note below).
 * <p>
 * This requires: feign.circuitbreaker.enabled=true in application.yml -
 * without it, @FeignClient's fallback is silently ignored and failures
 * propagate as raw exceptions.
 */
@Component
public class InventoryFeignClientFallback implements InventoryFeignClient {

    @Override
    public int checkStock(String productId) {
        System.out.println("Feign fallback: checkStock failed for " + productId + ", returning -1 (unknown)");
        return -1;
    }

    @Override
    public void reserveStock(String productId, int quantity, String requestId) {
        System.out.println("Feign fallback: reserveStock failed for " + productId
                + " (requestId=" + requestId + ") - reservation NOT made, caller must handle this.");
        // Note: for a write operation like this, silently swallowing the
        // failure is usually WRONG - the caller needs to know the
        // reservation didn't happen. Consider throwing a domain-specific
        // exception here instead of just returning silently, unlike the
        // read-only checkStock() above where a sentinel value is safer.
        throw new RuntimeException("Stock reservation unavailable - inventory service is down");
    }
}

/*
 * ALTERNATIVE: fallbackFactory (gives you the actual cause)
 * -------------------------------------------------------------
 * @FeignClient(..., fallbackFactory = InventoryFeignClientFallbackFactory.class)
 *
 * @Component
 * class InventoryFeignClientFallbackFactory implements FallbackFactory<InventoryFeignClient> {
 *     public InventoryFeignClient create(Throwable cause) {
 *         return new InventoryFeignClient() {
 *             public int checkStock(String productId) {
 *                 log.warn("checkStock failed: {}", cause.getMessage()); // <-- real cause available here
 *                 return -1;
 *             }
 *             ...
 *         };
 *     }
 * }
 */
