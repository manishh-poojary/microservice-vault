package com.toolkit.microservices.vault.resilience.ratelimiter;

import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * GeocodingClient
 * -----------------
 * Example: a third-party geocoding API only allows 10 requests/second on
 * our pricing tier. @RateLimiter protects us from ever exceeding that -
 * important because many third-party APIs will temporarily BAN your API
 * key/account if you exceed their quota too aggressively, which is a much
 * worse outcome than a few of our own requests being rejected gracefully.
 *
 * Same self-invocation caveat as the other annotations: call through a
 * Spring-managed bean, not `this.someMethod()`.
 */
@Service
public class GeocodingClient {

    private final RestTemplate restTemplate;

    public GeocodingClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @RateLimiter(name = "thirdPartyGeocodingApi", fallbackMethod = "geocodeFallback")
    public String geocode(String address) {
        System.out.println("Calling third-party geocoding API for: " + address);
        String url = "https://geocoding-provider.example.com/v1/lookup?address=" + address;
        return restTemplate.getForObject(url, String.class);
    }

    // RequestNotPermitted is the specific exception Resilience4j throws
    // when no permit is available and timeout-duration is 0 (or expires).
    private String geocodeFallback(String address, RequestNotPermitted ex) {
        System.out.println("Rate limit exceeded for geocoding API - request for '"
                + address + "' rejected: " + ex.getMessage());
        return "RATE_LIMITED - please retry shortly";
    }
}