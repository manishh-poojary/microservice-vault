package com.toolkit.microservices.vault.communication.asynchronous.webclient;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

/**
 * InventoryReactiveClient
 * ---------------------------
 * Demonstrates WebClient's reactive API: building a Mono, chaining
 * transformations, handling errors, applying a timeout, and retrying -
 * ALL as part of the reactive pipeline itself (rather than via
 * Resilience4j annotations wrapping a blocking method, as in the
 * RestTemplate/RestClient/Feign examples).
 * <p>
 * IMPORTANT: nothing in this class actually MAKES a network call or
 * blocks anything until something SUBSCRIBES to the returned Mono/Flux.
 * Spring WebFlux does this subscription automatically when a
 *
 * @RestController method returns Mono<T>/Flux<T> - the framework
 * subscribes on your behalf when writing the HTTP response. If you're
 * calling this from a traditional (non-reactive) Spring MVC controller,
 * see the .block() note at the bottom - and why you should mostly avoid it.
 */
@Service
public class InventoryReactiveClient {

    private final WebClient webClient;

    public InventoryReactiveClient(WebClient inventoryWebClient) {
        this.webClient = inventoryWebClient;
    }

    /**
     * Basic GET returning a Mono<Integer> - nothing happens until a
     * subscriber (a controller, a test, or a .block() call) triggers it.
     */
    public Mono<Integer> checkStock(String productId) {
        return webClient.get()
                .uri("/api/stock/{productId}", productId)
                .retrieve()
                .bodyToMono(Integer.class)

                // --- Timeout: part of the reactive chain itself ---
                .timeout(Duration.ofSeconds(2))

                // --- Retry: reactor's own retry operator (separate from
                // Resilience4j's @Retry, though Resilience4j ALSO provides
                // a reactive-aware RetryOperator if you want the same
                // config source/metrics as your other resilience patterns -
                // see the resilience4j-reactor note below) ---
                .retryWhen(Retry.backoff(3, Duration.ofMillis(200))
                        .filter(this::isRetryable))

                // --- Fallback: reactive equivalent of exceptionally()/onErrorResume ---
                .onErrorResume(this::isNotRetryable, ex -> {
                    System.out.println("Non-retryable error for " + productId + ": " + ex.getMessage());
                    return Mono.just(-1); // fallback value, same idea as earlier examples
                })
                .onErrorReturn(-1); // catch-all: if retries are exhausted or timeout fires, return fallback
    }

    private boolean isRetryable(Throwable ex) {
        if (ex instanceof WebClientResponseException wcre) {
            return wcre.getStatusCode().is5xxServerError(); // retry server errors, not client errors
        }
        return ex instanceof java.util.concurrent.TimeoutException;
    }

    private boolean isNotRetryable(Throwable ex) {
        return !isRetryable(ex);
    }

    /**
     * Combining multiple independent async calls - the reactive equivalent
     * of CompletableFuture.allOf()/thenCombine() from the multithreading
     * examples earlier in this repo. Mono.zip runs both calls CONCURRENTLY
     * and completes when both have responded.
     */
    public Mono<String> getProductSummary(String productId) {
        Mono<Integer> stockMono = checkStock(productId);
        Mono<String> priceMono = webClient.get()
                .uri("/api/price/{productId}", productId)
                .retrieve()
                .bodyToMono(String.class)
                .onErrorReturn("unknown");

        return Mono.zip(stockMono, priceMono)
                .map(tuple -> "Product " + productId + ": stock=" + tuple.getT1() + ", price=" + tuple.getT2());
    }

    /**
     * Flux example: streaming multiple results (e.g. server-sent events,
     * or just a large paginated collection consumed incrementally rather
     * than loaded all at once into memory).
     */
    public reactor.core.publisher.Flux<String> streamLowStockAlerts() {
        return webClient.get()
                .uri("/api/stock/low/stream")
                .retrieve()
                .bodyToFlux(String.class)
                .doOnNext(alert -> System.out.println("Received low-stock alert: " + alert));
    }

    /*
     * BLOCKING FROM A NON-REACTIVE CALLER (use sparingly!):
     *
     *   int stock = checkStock("ABC123").block();
     *
     * .block() defeats the entire purpose of using WebClient - it ties up
     * the calling thread waiting anyway, just like RestTemplate/RestClient
     * would have. It's acceptable in a FEW specific situations:
     *   - Tests
     *   - A one-off call in application startup code (@PostConstruct etc.)
     *   - Bridging a small piece of reactive code into an otherwise fully
     *     blocking codebase, as a temporary/pragmatic measure
     * It should NOT become the default way you consume WebClient calls in
     * a Spring MVC controller - if you're blocking on every WebClient call
     * anyway, RestClient is simpler and more honest about what's happening.
     */
}