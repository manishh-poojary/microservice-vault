package com.toolkit.microservices.vault.resilience.ratelimiter;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class RateLimiter {

    static <T> T executeWithRateLimit(BooleanSupplier tryAcquire,
                                      Supplier<T> operation, Supplier<T> rejectedResponse) {
        if (!tryAcquire.getAsBoolean()) {
            return rejectedResponse.get();
        }
        return operation.get();
    }

    public static void main(String[] args) throws InterruptedException {
// --- Fixed Window demo ---
        System.out.println("=== Fixed Window Rate Limiter: 3 requests per 1000ms window ===");
        FixedWindowRateLimiter fixedWindow = new FixedWindowRateLimiter(3, 1000);

        for (int i = 1; i <= 6; i++) {
            boolean allowed = fixedWindow.tryAcquire();
            System.out.println("Request " + i + ": " + (allowed ? "ALLOWED" : "REJECTED (429 Too Many Requests)"));
            Thread.sleep(200); // 6 requests over 1200ms - spans across window boundary
        }

        // --- Token Bucket demo ---
        System.out.println("\n=== Token Bucket Rate Limiter: capacity=3, refill=2 tokens/sec ===");
        TokenBucketRateLimiter tokenBucket = new TokenBucketRateLimiter(3, 2);

        System.out.println("Burst of 5 immediate requests (bucket starts full at 3 tokens):");
        for (int i = 1; i <= 5; i++) {
            boolean allowed = tokenBucket.tryAcquire();
            System.out.println("  Request " + i + ": " + (allowed ? "ALLOWED (consumed a token)" : "REJECTED (bucket empty)"));
        }

        System.out.println("\nWaiting 1000ms for tokens to refill (~2 tokens should regenerate)...");
        Thread.sleep(1000);

        System.out.println("Trying 3 more requests after refill:");
        for (int i = 1; i <= 3; i++) {
            boolean allowed = tokenBucket.tryAcquire();
            System.out.println("  Request " + i + ": " + (allowed ? "ALLOWED" : "REJECTED"));
        }

        // --- Applying it to actual calls, with a rejection fallback ---
        System.out.println("\n=== Applying rate limiter to real calls with a fallback response ===");
        TokenBucketRateLimiter apiLimiter = new TokenBucketRateLimiter(2, 1);
        for (int i = 1; i <= 4; i++) {
            String result = RateLimiter.executeWithRateLimit(
                    apiLimiter::tryAcquire,
                    () -> "Processed request " + (int) (Math.random() * 1000),
                    () -> "429 Too Many Requests - please slow down"
            );
            System.out.println("Call " + i + " -> " + result);
        }

    }
}

class FixedWindowRateLimiter {
    private final int maxRequestsPerWindow;
    private final long windowSizeMs;
    private long currentWindowStart;
    private final AtomicInteger requestsInWindow = new AtomicInteger(0);

    FixedWindowRateLimiter(int maxRequestsPerWindow, long windowSizeMs) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.windowSizeMs = windowSizeMs;
        this.currentWindowStart = System.currentTimeMillis();
    }

    synchronized boolean tryAcquire() {
        long now = System.currentTimeMillis();
        if (now - currentWindowStart >= windowSizeMs) {
            // New window - reset the counter
            currentWindowStart = now;
            requestsInWindow.set(0);
        }
        return requestsInWindow.incrementAndGet() <= maxRequestsPerWindow;
    }
}

class TokenBucketRateLimiter {
    private final int bucketCapacity;
    private final double refillTokensPerMs;
    private double availableTokens;
    private long lastRefillTimestamp;

    TokenBucketRateLimiter(int bucketCapacity, int refillRatePerSecond) {
        this.bucketCapacity = bucketCapacity;
        this.availableTokens = bucketCapacity; // start full
        this.refillTokensPerMs = refillRatePerSecond / 1000.0;
        this.lastRefillTimestamp = System.currentTimeMillis();
    }

    synchronized boolean tryAcquire() {
        refill();
        if (availableTokens >= 1) {
            availableTokens -= 1;
            return true;
        }
        return false;
    }

    private void refill() {
        long now = System.currentTimeMillis();
        long elapsedMs = now - lastRefillTimestamp;
        if (elapsedMs > 0) {
            double tokensToAdd = elapsedMs * refillTokensPerMs;
            availableTokens = Math.min(bucketCapacity, availableTokens + tokensToAdd);
            lastRefillTimestamp = now;
        }
    }
}
