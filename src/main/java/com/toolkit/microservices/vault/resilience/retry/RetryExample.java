package com.toolkit.microservices.vault.resilience.retry;

import java.util.function.Supplier;

public class RetryExample {

    public static void main(String[] args) {
        FlakyService flakyService = new FlakyService();
        RetryExecutor retryExecutor = new RetryExecutor(4, 200, 2.0); // 4 attempts, starting at 1s, doubling each time
        try {
            String result = retryExecutor.execute(flakyService::call);
            System.out.println("Final Result: " + result);
        } catch (Exception e) {
            System.err.println("Operation failed after retries: " + e.getMessage());
        }

        flakyService.resetCount(); // Reset the call count for the next retry executor
        RetryExecutor retryExecutor1 = new RetryExecutor(2, 100, 1.5); // 2 attempts, starting at 100ms, increasing by 1.5x each time
        try {
            String result = retryExecutor1.execute(flakyService::call);
            System.out.println("Final Result: " + result);
        } catch (Exception e) {
            System.err.println("Operation failed after retries: " + e.getMessage());
        }
    }
}

class RetryExecutor {

    private final int maxAttempts; // Maximum number of retry attempts
    private final long initialDelayMs; // Initial delay in milliseconds before the first retry
    private final double backoffMultiplier; // Multiplier for exponential backoff (e.g., 2.0 means double the delay each retry)

    public RetryExecutor(int maxAttempts, long initialDelayMs, double backoffMultiplier) {
        this.maxAttempts = maxAttempts;
        this.initialDelayMs = initialDelayMs;
        this.backoffMultiplier = backoffMultiplier;
    }

    <T> T execute(Supplier<T> action) throws Exception {
        long delay = initialDelayMs;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                System.out.println("Attempt " + attempt + " of " + maxAttempts + "...");
                return action.get(); // success -> return immediately, no more retries
            } catch (Exception e) {
                lastException = e;
                System.out.println("  Attempt " + attempt + " failed: " + e.getMessage());

                if (attempt < maxAttempts) {
                    System.out.println("  Waiting " + delay + "ms before next attempt...");
                    sleepQuietly(delay);
                    delay = (long) (delay * backoffMultiplier); // exponential backoff
                }
            }
        }

        // All attempts exhausted - give up and surface the last failure
        throw new RuntimeException("Operation failed after " + maxAttempts + " attempts", lastException);
    }

    static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

class FlakyService {
    private int callCount = 0;

    String call() {
        callCount++;
        if (callCount < 3) {
            throw new RuntimeException("Service temporarily unavailable (simulated)");
        }
        return "Success! Response from downstream service.";
    }

    void resetCount() {
        callCount = 0;
    }
}
