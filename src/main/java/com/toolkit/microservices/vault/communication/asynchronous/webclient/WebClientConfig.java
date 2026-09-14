package com.toolkit.microservices.vault.communication.asynchronous.webclient;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.util.concurrent.TimeUnit;

/**
 * WebClientConfig
 * ------------------
 * WebClient is Spring's REACTIVE, NON-BLOCKING HTTP client. Unlike
 * RestClient/RestTemplate, calls return Mono<T> (0-or-1 result) or
 * Flux<T> (0-to-N results, e.g. a stream) immediately - the calling
 * thread is NOT blocked waiting for the response. The actual HTTP I/O
 * happens on Netty's event-loop threads, and your code is notified via
 * callbacks/operators when data arrives.
 * <p>
 * WHEN THIS ACTUALLY MATTERS:
 * In a traditional Spring MVC (servlet-based) app, each incoming
 * request ties up one thread from the servlet container's thread pool
 * for its ENTIRE duration - including time spent blocked waiting on
 * downstream calls. With enough concurrent slow requests, you exhaust
 * the thread pool even though those threads are just sitting idle,
 * waiting.
 * <p>
 * In a WebFlux (reactive) app, or even in an MVC app making CONCURRENT
 * non-blocking outbound calls, threads are freed up while waiting -
 * a small number of threads can service a much larger number of
 * in-flight requests. This is the actual payoff of "asynchronous" here:
 * better resource utilization under high concurrency, not "faster"
 * for any single call.
 * <p>
 * Like RestClient, timeouts are configured on the underlying HTTP engine
 * (Reactor Netty here), not on WebClient itself.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient inventoryWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 500)     // connection establishment timeout
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(2, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(2, TimeUnit.SECONDS)));

        return WebClient.builder()
                .baseUrl("http://inventory-service")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Accept", "application/json")
                .filter(correlationIdFilter())   // ExchangeFilterFunction = WebClient's equivalent of an interceptor
                .filter(loggingFilter())
                .build();
    }

    private ExchangeFilterFunction correlationIdFilter() {
        return (request, next) -> {
            var mutated = ClientRequest.from(request)
                    .header("X-Correlation-Id", java.util.UUID.randomUUID().toString())
                    .build();
            return next.exchange(mutated);
        };
    }

    private ExchangeFilterFunction loggingFilter() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            System.out.println("WebClient request: " + request.method() + " " + request.url());
            return Mono.just(request);
        });
    }
}