package com.toolkit.microservices.vault.resilience.ratelimiter;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * PublicApiRateLimitFilter
 * ---------------------------
 * The @RateLimiter annotation protects OUTBOUND calls (us calling someone
 * else, like GeocodingClient above). It's equally common to need the
 * OPPOSITE: protecting YOUR OWN public API from being overwhelmed by
 * INBOUND traffic (whether malicious, buggy client retry loops, or just
 * unexpectedly high demand).
 * <p>
 * This shows a simple Servlet Filter applying a Resilience4j RateLimiter
 * to all inbound requests - a common pattern for protecting an API at the
 * edge, often used alongside (not instead of) an API Gateway's own rate
 * limiting.
 */
@Component
public class PublicApiRateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;

    public PublicApiRateLimitFilter(RateLimiterRegistry registry) {
        this.rateLimiter = registry.rateLimiter("internalApiPublic");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            RateLimiter.decorateCheckedRunnable(rateLimiter,
                    () -> filterChain.doFilter(request, response)).run();
        } catch (RequestNotPermitted e) {
            response.setStatus(429); // 429 Too Many Requests
            response.getWriter().write("Rate limit exceeded. Please slow down and try again shortly.");
        } catch (Throwable e) {
            throw new ServletException(e);
        }
    }
}
