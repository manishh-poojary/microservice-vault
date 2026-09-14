package com.toolkit.microservices.vault.communication.synchronous.rest.client;

import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

/**
 * InventoryClient
 * -----------------
 * Demonstrates the RestClient fluent API for GET, POST, and error
 * handling, PLUS the same Resilience4j @Retry composition pattern used
 * with RestTemplate and Feign in earlier examples - the resilience layer
 * doesn't care which HTTP client you're using underneath.
 */
@Service
public class InventoryClient {

    private final RestClient restClient;

    /**
     * The RestClient bean from RestClientConfig is injected here - RestClient
     * instances are meant to be built ONCE (they're immutable/thread-safe)
     * and reused across all calls, not rebuilt per-request.
     */
    public InventoryClient(RestClient inventoryRestClient) {
        this.restClient = inventoryRestClient;
    }

    /**
     * Fluent chain: method -> uri -> retrieve -> extract body.
     * Compare this to RestTemplate's restTemplate.getForObject(url, Integer.class) -
     * more verbose here, but far more flexible for headers/error handling/etc.
     */
    @Retry(name = "inventoryService", fallbackMethod = "checkStockFallback")
    public Integer checkStock(String productId) {
        return restClient.get()
                .uri("/api/stock/{productId}", productId)
                .retrieve()
                .body(Integer.class);
    }

    /**
     * Reserve stock for a given product.
     *
     * @param productId the product ID
     * @param quantity  the quantity to reserve
     */
    public void reserveStock(String productId, int quantity) {
        restClient.post()
                .uri("/api/stock/{productId}/reserve", productId)
                .header("X-Request-Id", java.util.UUID.randomUUID().toString())
                .body(new ReserveRequest(quantity))
                .retrieve()
                .toBodilessEntity(); // POST with no meaningful response body, just confirm success
    }

    /**
     * ParameterizedTypeReference needed for generic types (List<String>)
     * same as RestTemplate's exchange() with ParameterizedTypeReference -
     * RestClient makes this less awkward via body(new ParameterizedTypeReference<>(){})
     */
    public List<String> getLowStockProducts() {
        return restClient.get()
                .uri("/api/stock/low")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    /**
     * onStatus-based error handling INLINE for a specific call (as opposed
     * to the defaultStatusHandler configured globally on the RestClient
     * bean in RestClientConfig - that one applies to ALL calls made with
     * this client; this shows how to add call-SPECIFIC handling on top).
     */
    public Integer checkStockWithCustomErrorHandling(String productId) {
        return restClient.get()
                .uri("/api/stock/{productId}", productId)
                .retrieve()
                .onStatus(status -> status.value() == 503, (request, response) -> {
                    throw new IllegalStateException("Inventory service temporarily unavailable");
                })
                .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                    throw new RuntimeException("Inventory service server error: " + response.getStatusCode());
                })
                .body(Integer.class);
    }

    private int checkStockFallback(String productId, RestClientResponseException ex) {
        System.out.println("checkStock failed for " + productId
                + " (status " + ex.getStatusCode() + ") - returning fallback value.");
        return -1;
    }

    /**
     * Multiple fallback method OVERLOADS are supported by Resilience4j -
     * it picks the most specific one matching the thrown exception type.
     * This lets a 404 (mapped to ProductNotFoundException by the global
     * defaultStatusHandler) get DIFFERENT handling than other HTTP errors.
     */
    private int checkStockFallback(String productId, RestClientConfig.ProductNotFoundException ex) {
        System.out.println("Product " + productId + " does not exist - returning 0, " +
                "not -1 (distinct from 'unknown').");
        return 0;
    }

    record ReserveRequest(int quantity) {
    }
}
