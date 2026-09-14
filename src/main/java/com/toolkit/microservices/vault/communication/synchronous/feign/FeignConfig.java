package com.toolkit.microservices.vault.communication.synchronous.feign;

import feign.Logger;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

/**
 * FeignConfig
 * -------------
 * Deliberately NOT annotated with @Configuration at the top level in a way
 * that gets component-scanned globally - it's referenced explicitly via
 *
 * @FeignClient(configuration = FeignConfig.class) on InventoryFeignClient,
 * so these beans apply ONLY to that one client, not every Feign client in
 * the app. (If it WAS caught by component scanning, these beans would
 * become global defaults for all clients - sometimes desired, sometimes not.)
 */
public class FeignConfig {

    /**
     * Logging level for this client. NONE (default), BASIC, HEADERS, FULL.
     * FULL logs headers + body + metadata - extremely useful in dev,
     * usually too noisy/leaky (auth headers!) for production.
     */
    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    /**
     * Runs before every outgoing request made by this client - the
     * standard place to attach auth tokens, correlation/trace IDs, etc.
     */
    @Bean
    RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            requestTemplate.header("X-Internal-Auth", "service-to-service-token");
            requestTemplate.header("X-Correlation-Id", java.util.UUID.randomUUID().toString());
        };
    }

    /**
     * Custom error decoding - lets you turn raw HTTP error responses into
     * meaningful, specific exceptions your fallback/catch logic can react
     * to differently (see CustomErrorDecoder.java).
     */
    @Bean
    ErrorDecoder errorDecoder() {
        return new CustomErrorDecoder();
    }
}
