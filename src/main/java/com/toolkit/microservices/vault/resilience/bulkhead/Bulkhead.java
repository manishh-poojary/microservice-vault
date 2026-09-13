package com.toolkit.microservices.vault.resilience.bulkhead;

import java.util.concurrent.*;
import java.util.function.Supplier;

public class Bulkhead {

    public static void main(String[] args) {
        try {
            ThreadPoolBulkhead.demoThreadBulkHead();
            SemaphoreBulkhead.demoSemaphoreBulkhead();
        } catch (InterruptedException e) {
            System.out.println("Demo interrupted: " + e.getMessage());
        }
    }
}

class ThreadPoolBulkhead {
    private final ExecutorService pool;
    private final String name;

    ThreadPoolBulkhead(String name, int poolSize) {
        this.name = name;
        this.pool = Executors.newFixedThreadPool(poolSize);
    }

    <T> Future<T> submit(Callable<T> task) {
        return pool.submit(task);
    }

    void shutdown() {
        pool.shutdown();
    }

    // Simulates a slow/stuck downstream service (e.g. a hanging dependency)
    static String slowCall(String dependency, long delayMs) throws InterruptedException {
        Thread.sleep(delayMs);
        return dependency + " responded after " + delayMs + "ms";
    }

    static void demoThreadBulkHead() throws InterruptedException {
        System.out.println("=== WITHOUT bulkhead: shared pool gets starved ===");
        ExecutorService sharedPool = Executors.newFixedThreadPool(4);

        // Flood the shared pool with slow "PaymentService" calls
        for (int i = 0; i < 4; i++) {
            int id = i;
            sharedPool.submit(() -> {
                try {
                    System.out.println("[Shared Pool] PaymentService call " + id + " started (will hang 3s)...");
                    slowCall("PaymentService", 3000);
                } catch (InterruptedException ignored) {
                    System.out.println("PaymentService call failed: " + ignored.getMessage());
                }
            });
        }

        // Now a call to a totally healthy "UserService" has to WAIT - no threads free!
        Future<String> userServiceCall = sharedPool.submit(() -> slowCall("UserService", 100));
        long start = System.currentTimeMillis();
        try {
            String result = userServiceCall.get(); // this will be delayed by the flood above
            long waited = System.currentTimeMillis() - start;
            System.out.println("[Shared Pool] UserService result: " + result
                    + " (had to wait " + waited + "ms because pool was starved by PaymentService!)");
        } catch (ExecutionException e) {
            System.out.println("UserService call failed: " + e.getMessage());
        }
        sharedPool.shutdown();


        // --- Now WITH bulkhead isolation ---
        System.out.println("=== WITH bulkhead: isolated pools protect each other ===");
        ThreadPoolBulkhead paymentBulkhead = new ThreadPoolBulkhead("PaymentService", 4);
        ThreadPoolBulkhead userBulkhead = new ThreadPoolBulkhead("UserService", 4);

        for (int i = 0; i < 4; i++) {
            int id = i;
            paymentBulkhead.submit(() -> {
                System.out.println("[Payment Bulkhead] call " + id + " started (will hang 3s)...");
                return slowCall("PaymentService", 3000);
            });
        }

        Future<String> isolatedUserCall = userBulkhead.submit(() -> slowCall("UserService", 100));
        start = System.currentTimeMillis();
        try {
            String result = isolatedUserCall.get();
            long waited = System.currentTimeMillis() - start;
            System.out.println("[User Bulkhead] UserService result: " + result
                    + " (only waited " + waited + "ms - UNAFFECTED by PaymentService being stuck!)");
        } catch (ExecutionException e) {
            System.out.println("UserService call failed: " + e.getMessage());
        }

        paymentBulkhead.shutdown();
        userBulkhead.shutdown();
    }
}

class SemaphoreBulkhead {

    private final Semaphore semaphore;
    private final String name;

    public SemaphoreBulkhead(String name, int maxConcurrentCalls) {
        this.name = name;
        this.semaphore = new Semaphore(maxConcurrentCalls);
    }

    <T> T execute(Callable<T> task, Supplier<T> fallback) throws Exception {
        if (!semaphore.tryAcquire()) {
            System.out.println("  [" + name + " Bulkhead] REJECTED - max concurrent calls ("
                    + semaphore.availablePermits() + " available) reached. Failing fast.");
            return fallback.get();
        }
        try {
            return task.call();
        } finally {
            semaphore.release();
        }
    }

    static void demoSemaphoreBulkhead() throws InterruptedException {
        System.out.println("\n=== Semaphore Bulkhead: limiting concurrent calls to 2 ===");
        SemaphoreBulkhead bulkhead = new SemaphoreBulkhead("ReportService", 2);
        ExecutorService callers = Executors.newFixedThreadPool(5);

        for (int i = 1; i <= 5; i++) {
            int id = i;
            callers.submit(() -> bulkhead.execute(
                    () -> {
                        System.out.println("  Call " + id + " acquired permit, working...");
                        Thread.sleep(500);
                        return "Call " + id + " done";
                    }, () -> {
                        System.out.println("  Call " + id + " rejected, using fallback.");
                        return "Call " + id + " failed";
                    }
            ));
            Thread.sleep(50); // stagger slightly so we can observe rejections
        }

        callers.shutdown();
        callers.awaitTermination(5, TimeUnit.SECONDS);
    }
}