# RestClient vs RestTemplate vs WebClient vs Feign

Quick reference for choosing an HTTP client in a Spring Boot microservice.

| Client | Style | Blocking? | Best For |
|---|---|---|---|
| **RestTemplate** | Imperative (`getForObject`, `postForEntity`) | Yes | Legacy code, minimal new usage recommended |
| **RestClient** | Fluent/chainable | Yes | New synchronous code - the modern default for blocking Spring MVC apps |
| **WebClient** | Fluent, reactive (`Mono`/`Flux`) | No (non-blocking) | Reactive stacks (WebFlux), or when you need true async/streaming |
| **OpenFeign** | Declarative interface, no implementation code | Yes (by default) | When you want the LEAST boilerplate - annotate an interface, done |

## Decision guide

- **Building a new synchronous Spring MVC service?** → `RestClient`
- **Already using WebFlux / need reactive streams / server-sent events?** → `WebClient`
- **Want minimal code and don't mind a bit of "magic" via annotations?** → `OpenFeign` (which, as of Spring Cloud OpenFeign 4.1+, can actually use `RestClient` as its underlying HTTP engine instead of the default `feign.Client` - configurable via `spring.cloud.openfeign.client.config` or by supplying a custom `feign.Client` bean backed by `RestClient`)
- **Maintaining older code that already uses RestTemplate?** → No urgent need to migrate; RestTemplate isn't deprecated, just not getting new features

## They all compose with Resilience4j the same way

Regardless of which client you pick, the resilience layer (`@Retry`,
`@CircuitBreaker`, `@Bulkhead`, `@TimeLimiter`, `@RateLimiter`) wraps the
**method that calls the client**, not the client itself. This is why every
example in this repo's `microservice-patterns/` folder looks structurally
identical at the annotation level, whether the method underneath uses
`RestTemplate`, `RestClient`, `WebClient`, or a Feign interface.