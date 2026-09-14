# API Gateway Pattern

## The problem

Without a gateway, clients (web app, mobile app, third parties) call
microservices DIRECTLY - meaning every client needs to know every
service's address, every service needs to implement its own auth, and
any cross-cutting concern (rate limiting, logging, CORS) gets
duplicated across every single service.

## The pattern

A single entry point sits in front of all your microservices. Clients
only ever talk to the gateway; the gateway routes requests to the
correct downstream service and handles cross-cutting concerns centrally.

```
Client ──► API Gateway ──► Inventory Service
                       ├──► Order Service
                       └──► Catalog Service
```

## What this example demonstrates

- **Routing** — `Path` predicates + `lb://` (load-balanced, service-discovery-aware) URIs route requests to the correct service
- **Path manipulation** — `StripPrefix` and `RewritePath` decouple the external API shape from each service's internal routes
- **Resilience, applied at the edge** — the `CircuitBreaker` and `Retry` filters wrap resilience4j the exact same way `@CircuitBreaker`/`@Retry` did on individual services in earlier examples — the gateway is just another caller
- **Rate limiting, per-user** — `RequestRateLimiter` + a `KeyResolver` bean scopes limits per caller instead of globally
- **Centralized auth** — `AuthenticationFilter` (a `GlobalFilter`) validates JWTs once, at the edge, so individual services don't each reimplement it — critical trust-boundary caveat: this only holds if services are network-isolated from being called directly, bypassing the gateway
- **Fallback, at the gateway level** — `FallbackController` backs the circuit breaker's `fallbackUri`, same Fallback pattern as earlier, just triggered centrally

## API Gateway vs Backend for Frontend (BFF)

A closely related pattern worth knowing: instead of ONE gateway serving
ALL clients (web, mobile, partner APIs) with the SAME shape, a **BFF**
gives each client type its own dedicated gateway, tailored to what that
specific client needs (e.g. a mobile BFF might aggregate/trim responses
more aggressively to save bandwidth than a web BFF would). Use a single
API Gateway when your clients have similar needs; reach for BFF when a
mobile app and a web app genuinely want different shaped responses from
the same underlying services.

## Where this fits with everything else in this repo

The gateway doesn't replace the resilience patterns built into each
individual service — it ADDS a layer of the same protections at the
edge. A well-built system typically has:
- `@Retry`/`@CircuitBreaker`/`@Bulkhead` inside Inventory Service, protecting IT from ITS OWN downstream dependencies
- The SAME categories of protection again at the Gateway, protecting the whole system's entry point from being overwhelmed or from cascading failures reaching clients directly

They're complementary layers, not a replacement for each other.