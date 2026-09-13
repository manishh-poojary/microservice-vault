package com.toolkit.microservices.vault.resilience.bulkhead;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;

/**
 * PaymentClient
 * ---------------
 * Demonstrates BOTH bulkhead types side by side.
 * <p>
 * As with @Retry and @CircuitBreaker, self-invocation bypasses the proxy -
 * always call these methods from a different Spring bean.
 */
@Service
public class InventoryClient {

    private final RestTemplate restTemplate;

    public InventoryClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * SEMAPHORE bulkhead (Type.SEMAPHORE is actually the default - shown
     * explicitly here for clarity). Limits concurrent calls without
     * dedicating separate threads. Good default choice for most services.
     */
    @Bulkhead(name = "paymentService", type = Bulkhead.Type.SEMAPHORE, fallbackMethod = "processPaymentFallback")
    public String getStockInfo(String productId) {
        // Implementation for fetching stock information
        return "Stock info for " + productId;
    }

    /**
     * THREAD POOL bulkhead - dedicates an isolated pool of threads.
     * Notice the return type MUST be CompletableFuture<T> for this type -
     * Resilience4j runs the method body on the dedicated pool asynchronously.
     * Use this when you want TRUE isolation, including from thread starvation
     * caused by slow/blocking calls (e.g. a legacy dependency with no
     * async client available).
     */
    @Bulkhead(name = "paymentServiceAsync", type = Bulkhead.Type.THREADPOOL, fallbackMethod = "processPaymentAsyncFallback")
    public CompletableFuture<String> processPaymentAsync(String orderId) {
        System.out.println("Processing payment (async, isolated pool) for order: " + orderId);
        String url = "http://payment-service/api/pay/" + orderId;
        String result = restTemplate.postForObject(url, null, String.class);
        return CompletableFuture.completedFuture(result);
    }

    // Fallback for SEMAPHORE bulkhead - triggered when max-concurrent-calls
    // is reached and max-wait-duration expires without a free slot.
    private String processPaymentFallback(String orderId, Throwable t) {
        System.out.println("Payment bulkhead full for order " + orderId + " - reason: " + t.getMessage());
        return "PENDING - payment queued for retry (bulkhead full)";
    }

    // Fallback for THREADPOOL bulkhead - same idea, but triggered when the
    // dedicated pool's threads + queue are all occupied.
    private CompletableFuture<String> processPaymentAsyncFallback(String orderId, Throwable t) {
        System.out.println("Payment thread pool bulkhead full for order " + orderId + " - reason: " + t.getMessage());
        return CompletableFuture.completedFuture("PENDING - payment queued for retry (thread pool full)");
    }
}
