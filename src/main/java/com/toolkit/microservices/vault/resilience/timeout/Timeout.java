package com.toolkit.microservices.vault.resilience.timeout;

import java.util.concurrent.*;

public class Timeout {

    // Simulates a service call that takes a variable amount of time
    static String slowServiceCall(long durationMs) throws InterruptedException {
        System.out.println("  Service call started, will take " + durationMs + "ms...");
        Thread.sleep(durationMs); // interruptible - respects Future.cancel(true)
        return "Response after " + durationMs + "ms";
    }

    public static void main(String[] args) throws Exception {
        TimeoutExecutor executor = new TimeoutExecutor(4);
        long timeoutBudgetMs = 1000;

        // --- Scenario 1: Call finishes WITHIN the timeout budget ---
        System.out.println("=== Scenario 1: Fast call completes within timeout ===");
        String result1 = executor.execute(
                () -> slowServiceCall(400),
                timeoutBudgetMs,
                () -> "FALLBACK: default response"
        );
        System.out.println("Result: " + result1 + "\n");

        // --- Scenario 2: Call exceeds the timeout budget ---
        System.out.println("=== Scenario 2: Slow call exceeds timeout ===");
        long start = System.currentTimeMillis();
        String result2 = executor.execute(
                () -> slowServiceCall(3000), // way longer than our 1000ms budget
                timeoutBudgetMs,
                () -> "FALLBACK: default response"
        );
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("Result: " + result2);
        System.out.println("Caller only waited ~" + elapsed + "ms, NOT the full 3000ms the call would have taken.\n");

        // --- Scenario 3: Combining Timeout + Retry ---
        // A common real-world combo: each individual attempt has its own
        // timeout budget, and failures (including timeouts) trigger a retry.
        System.out.println("=== Scenario 3: Timeout + Retry combined ===");
        int maxAttempts = 3;
        String finalResult = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            System.out.println("Attempt " + attempt + " of " + maxAttempts + ":");
            // first 2 attempts "hang", 3rd is fast
            final long duration = attempt < 3 ? 2000 : 300;

            String attemptResult = executor.execute(
                    () -> slowServiceCall(duration),
                    timeoutBudgetMs,
                    () -> null // null signals "this attempt failed" so we know to retry
            );

            if (attemptResult != null) {
                finalResult = attemptResult;
                System.out.println("  Attempt " + attempt + " succeeded within timeout.\n");
                break;
            } else {
                System.out.println("  Attempt " + attempt + " timed out, retrying...\n");
            }
        }
        System.out.println("Final combined result: " + finalResult);

        executor.shutdown();
    }
}

class TimeoutExecutor {

    private final ExecutorService executorService;

    public TimeoutExecutor(int poolSize) {
        this.executorService = Executors.newFixedThreadPool(poolSize);
    }

    <T> T execute(Callable<T> task, long timeoutMillis, Supplier<T> fallback) throws Exception {
        Future<T> future = executorService.submit(task);
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS); // blocks up to timeoutMs
        } catch (TimeoutException e) {
            System.out.println("  Operation timed out after " + timeoutMillis + "ms - giving up and using fallback.");
            future.cancel(true); // best-effort: sends interrupt if the task checks for it
            return fallback.get();
        } catch (Exception e) {
            System.out.println("  Operation failed: " + e.getMessage());
            return fallback.get();
        }
    }

    void shutdown() {
        executorService.shutdown();
    }

    interface Supplier<T> {
        T get();
    }


}
