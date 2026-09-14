package com.toolkit.microservices.vault.communication.synchronous.feign;

import feign.Response;
import feign.codec.ErrorDecoder;

/**
 * CustomErrorDecoder
 * ---------------------
 * By default, Feign throws a generic FeignException for any non-2xx
 * response, which makes it hard to react differently to "not found"
 * vs "server error" vs "rate limited". This decoder maps status codes
 * to specific exceptions so upstream code (and Resilience4j's
 * record-exceptions / ignore-exceptions config) can distinguish them.
 * <p>
 * This matters directly for earlier patterns: e.g. you generally want
 * Retry to retry on 5xx/503 but NEVER retry on 404 "product not found" -
 * retrying won't make a nonexistent product exist.
 */
public class CustomErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        return switch (response.status()) {
            case 404 -> new ProductNotFoundException("Product not found: " + methodKey);
            case 429 -> new RateLimitedException("Downstream service rate-limited us: " + methodKey);
            case 503 -> new ServiceUnavailableException("Inventory service unavailable: " + methodKey);
            default ->
                // Fall back to Feign's default behavior for anything else
                // (still produces a FeignException, just not a specific one)
                    defaultDecoder.decode(methodKey, response);
        };
    }

    public static class ProductNotFoundException extends RuntimeException {
        public ProductNotFoundException(String msg) {
            super(msg);
        }
    }

    public static class RateLimitedException extends RuntimeException {
        public RateLimitedException(String msg) {
            super(msg);
        }
    }

    public static class ServiceUnavailableException extends RuntimeException {
        public ServiceUnavailableException(String msg) {
            super(msg);
        }
    }
}
