package com.toolkit.microservices.vault.communication.synchronous.rest.client;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * RestClientConfig
 * -------------------
 * RestClient (Spring Framework 6.1+ / Spring Boot 3.2+) is the modern
 * replacement recommendation for RestTemplate. It's still SYNCHRONOUS
 * (blocking) like RestTemplate - it's NOT the reactive/async client
 * (that's WebClient). Think of it as "RestTemplate's API redesigned with
 * the fluent, chainable style WebClient made popular", for teams that
 * don't need or want reactive programming.
 * <p>
 * WHY RestClient OVER RestTemplate FOR NEW CODE:
 * - RestTemplate is in maintenance mode (not deprecated, but no new
 * features land there - all new capability goes into RestClient)
 * - Fluent, chainable API (easier to read call chains)
 * - Cleaner functional-style error handling (onStatus with predicates)
 * - Shares infrastructure with WebClient (same ClientHttpRequestFactory
 * abstraction, same ExchangeFilterFunction-like interceptors)
 * <p>
 * WHY NOT WebClient INSTEAD:
 * - WebClient requires reactor-core and thinking in Mono/Flux even for
 * simple blocking use cases (you'd call .block() everywhere, which
 * defeats the purpose and is a common anti-pattern)
 * - RestClient gives you the same nice fluent API WITHOUT forcing
 * reactive programming into a traditional servlet-based Spring MVC app
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient inventoryRestClient() {
        return RestClient.builder()
                .baseUrl("http://inventory-service")
                .defaultHeader("Accept", "application/json")
                .requestFactory(clientHttpRequestFactory())
                .requestInterceptor((request, body, execution) -> {
                    // Runs on EVERY request made by this client - same idea
                    // as Feign's RequestInterceptor from the OpenFeign example.
                    request.getHeaders().add("X-Correlation-Id", java.util.UUID.randomUUID().toString());
                    return execution.execute(request, body);
                })
                .defaultStatusHandler(
                        status -> status.value() == 404,
                        (request, response) -> {
                            throw new ProductNotFoundException("Product not found: " + request.getURI());
                        })
                .build();
    }

    /**
     * Connect/read timeouts MUST be configured on the underlying
     * ClientHttpRequestFactory - RestClient itself has no timeout
     * properties of its own, it delegates entirely to whatever HTTP
     * client library backs the request factory (here: Apache HttpClient 5,
     * a common production choice over the JDK's built-in client for
     * connection pooling control).
     */
    private ClientHttpRequestFactory clientHttpRequestFactory() {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(500))
                .setResponseTimeout(Timeout.ofMilliseconds(2000))
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();

        return new HttpComponentsClientHttpRequestFactory(httpClient);
    }

    public static class ProductNotFoundException extends RuntimeException {
        public ProductNotFoundException(String msg) {
            super(msg);
        }
    }
}
