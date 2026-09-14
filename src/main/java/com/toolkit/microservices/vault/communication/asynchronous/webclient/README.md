# Async HTTP: WebClient Notes

## What "asynchronous" actually buys you here

A single WebClient call isn't inherently *faster* than a RestClient call - the network round trip takes the same amount
of time either way. What changes is **what the calling thread does while waiting**:

- RestClient/RestTemplate: the calling thread blocks, sitting idle, until the response arrives.
- WebClient: the calling thread is freed immediately; a callback fires on an event-loop thread once the response
  arrives.

This matters most under **high concurrency** - a WebFlux app handling thousands of concurrent slow downstream calls can
do so with a small, fixed number of threads, whereas a traditional blocking app would need roughly one thread per
concurrent in-flight request (bounded by the servlet container's thread pool size).

## Resilience4j + reactive types

With `resilience4j-reactor` on the classpath, `@CircuitBreaker`,
`@RateLimiter`, `@Bulkhead`, and `@TimeLimiter` all work directly on methods returning `Mono<T>`/`Flux<T>` -
Resilience4j detects the reactive return type and applies its logic as an operator on the reactive chain, rather than
wrapping a blocking call. `@Retry` reactive support works the same way. In practice, many teams instead use Reactor's
own native
`.retryWhen()`/`.timeout()` operators directly (as shown in
`InventoryReactiveClient.java`) since they're arguably more idiomatic inside a reactive chain, and reserve the
Resilience4j annotations for their blocking (RestClient/Feign) call sites for consistency of config/metrics across the
whole app. Either approach is valid - pick one and stay consistent within a codebase.

## Next: true asynchronous COMMUNICATION (messaging)

Everything above is still fundamentally "Service A calls Service B and gets a response" - just non-blocking about HOW it
waits. **Event-driven messaging (Kafka/RabbitMQ)** is a different flavor of "asynchronous"
entirely: Service A doesn't wait for a response at all, it publishes an event and moves on; Service B (and possibly C,
D...) consume that event independently, whenever they're ready. That's the next topic queued up.