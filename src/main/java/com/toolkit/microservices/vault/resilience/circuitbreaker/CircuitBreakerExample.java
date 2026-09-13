package com.toolkit.microservices.vault.resilience.circuitbreaker;

import java.util.LinkedList;
import java.util.function.Supplier;

public class CircuitBreakerExample {

    public static void main(String[] args) throws Exception {
        // Configure the circuit breaker
        CircuitBreaker circuitBreaker = new CircuitBreaker(
                5,      // sliding window size
                0.5,    // failure rate threshold (50%)
                5000,   // wait time in OPEN state (5 seconds)
                3       // trial calls allowed in HALF_OPEN state
        );

        // Simulate a service that fails for the first 10 seconds
        UnreliableService service = new UnreliableService(10000);

        for (int i = 1; i <= 20; i++) {
            System.out.println("Call #" + i + " - Circuit State: " + circuitBreaker.getState());
            try {
                String result = circuitBreaker.execute(service::call, () -> "Fallback response");
                System.out.println("  Result: " + result);
            } catch (Exception e) {
                System.out.println("  Exception: " + e.getMessage());
            }
            Thread.sleep(1000); // wait 1 second between calls
        }
    }

}

enum State {CLOSED, OPEN, HALF_OPEN}

class CircuitBreaker {
    private final int slidingWindowSize;       // how many recent calls to track
    private final double failureRateThreshold; // e.g. 0.5 = 50%
    private final long openStateWaitMs;        // how long to stay OPEN before trying again
    private final int halfOpenTrialCalls;       // how many test calls allowed in HALF_OPEN

    private final LinkedList<Boolean> callResults = new LinkedList<>(); // true = success
    private State state = State.CLOSED;
    private long openedAtMs = 0;
    private int halfOpenCallsMade = 0;
    private int halfOpenSuccesses = 0;

    public CircuitBreaker(int slidingWindowSize, double failureRateThreshold,
                          long openStateWaitMs, int halfOpenTrialCalls) {
        this.slidingWindowSize = slidingWindowSize;
        this.failureRateThreshold = failureRateThreshold;
        this.openStateWaitMs = openStateWaitMs;
        this.halfOpenTrialCalls = halfOpenTrialCalls;
    }

    private void transitionToOpen() {
        state = State.OPEN;
        openedAtMs = System.currentTimeMillis();
        callResults.clear();
        halfOpenCallsMade = 0;
        halfOpenSuccesses = 0;
        System.out.println("  >>> Circuit is now OPEN <<<");
    }

    private void transitionToHalfOpen() {
        state = State.HALF_OPEN;
        halfOpenCallsMade = 0;
        halfOpenSuccesses = 0;
        System.out.println("  >>> Wait duration elapsed - circuit is now HALF_OPEN (testing recovery) <<<");
    }

    private void transitionToClosed() {
        state = State.CLOSED;
        callResults.clear();
        System.out.println("  >>> Trial calls succeeded - circuit is now CLOSED (normal operation resumed) <<<");
    }

    State getState() {
        return state;
    }

    synchronized <T> T execute(Supplier<T> action, Supplier<T> fallback) throws Exception {
        if (state == State.OPEN) {
            if (System.currentTimeMillis() - openedAtMs >= openStateWaitMs) {
                transitionToHalfOpen();
            } else {
                throw new RuntimeException("Circuit is OPEN - call blocked");
            }
        }

        // In HALF_OPEN, only allow a limited number of trial calls through
        if (state == State.HALF_OPEN && halfOpenCallsMade >= halfOpenTrialCalls) {
            System.out.println("  [HALF_OPEN] Trial call budget used up - failing fast until result is decided.");
            return fallback.get();
        }

        try {
            T result = action.get();
            recordResult(true);
            return result;
        } catch (Exception e) {
            recordResult(false);
            throw e;
        }
    }

    private void recordResult(boolean success) {
        if (state == State.HALF_OPEN) {
            halfOpenCallsMade++;
            if (success) halfOpenSuccesses++;
            System.out.println("  [HALF_OPEN] Trial call " + halfOpenCallsMade + "/" + halfOpenTrialCalls
                    + " -> " + (success ? "SUCCESS" : "FAILURE"));

            if (halfOpenCallsMade >= halfOpenTrialCalls) {
                // Decide whether to close the circuit or re-open it
                if (halfOpenSuccesses == halfOpenTrialCalls) {
                    transitionToClosed();
                } else {
                    transitionToOpen();
                }
            }
            return;
        }

        // CLOSED state: maintain a sliding window of recent results
        callResults.addLast(success);
        if (callResults.size() > slidingWindowSize) callResults.removeFirst();

        if (callResults.size() == slidingWindowSize) {
            long failures = callResults.stream().filter(r -> !r).count();
            double failureRate = (double) failures / slidingWindowSize;
            if (failureRate >= failureRateThreshold) {
                System.out.println("  Failure rate " + (failureRate * 100) + "% >= threshold "
                        + (failureRateThreshold * 100) + "% -> OPENING circuit!");
                transitionToOpen();
            }
        }
    }
}

class UnreliableService {
    private final long failUntilMs;

    UnreliableService(long failForMs) {
        this.failUntilMs = System.currentTimeMillis() + failForMs;
    }

    String call() {
        if (System.currentTimeMillis() < failUntilMs) {
            throw new RuntimeException("Service unavailable");
        }
        return "OK response from service";
    }
}