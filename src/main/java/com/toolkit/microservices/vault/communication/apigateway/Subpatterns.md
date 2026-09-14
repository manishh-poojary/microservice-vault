# API Gateway Sub-Patterns

"API Gateway" is really an umbrella term for a few distinct things a
gateway can do. Most real gateways combine all of them, but it's worth
knowing them as separate concerns:

## 1. Gateway Routing (what we built last time)

The gateway is a **reverse proxy**: it inspects the incoming request
(path, host, headers) and forwards it to the correct downstream service,
unchanged in shape. One request in, one request out, no combining of data.

```
Client ──► Gateway ──► Order Service   (1-to-1, just routed)
```

This is what `application.yml`'s `routes` section did in the previous
example - `Path=/api/orders/**` → `lb://order-service`.

## 2. Gateway Aggregation (new - shown in `AggregationController.java`)

The gateway calls **multiple** downstream services for a single incoming
client request, and combines their responses into one payload before
replying. This exists specifically to help THIN CLIENTS (mobile apps
especially) avoid making several round trips over a slow/unreliable
network connection.

```
                    ┌──► Order Service ────┐
Client ──► Gateway ─┼──► Inventory Service ─┼──► combined response ──► Client
                    └──► Customer Service ──┘
```

Trade-off: the gateway now has BUSINESS LOGIC (knowing how to combine
these responses), which pulls it slightly away from being a "dumb" pure
routing layer. Keep aggregation logic thin - if it grows complex, that's
usually a sign you actually want a dedicated BFF (see below) rather than
piling aggregation logic into a shared general-purpose gateway.

## 3. Gateway Offloading (new - shown in `application.yml` additions)

Cross-cutting concerns that EVERY service would otherwise need to
implement themselves get handled ONCE, centrally, at the gateway instead:

- SSL/TLS termination (gateway handles HTTPS, talks plain HTTP internally within the trusted network)
- Response compression
- Response caching for cacheable GETs
- Authentication (we already built this - `AuthenticationFilter.java` from the previous example)
- Rate limiting (also already built)

This is the pattern behind the general advice "put cross-cutting
concerns at the gateway" - offloading is the formal name for that idea.

## 4. Backend for Frontend (BFF) - a structural variant, not a new capability

Already covered in the previous README - instead of ONE gateway serving
ALL client types identically, each client type (web, mobile, partner API)
gets its OWN dedicated gateway/aggregation layer, tuned to what that
specific client needs. BFF often uses Aggregation heavily internally -
they're complementary, not competing, ideas.

## How these map onto what's ALREADY in this repo

| Sub-pattern | Where it lives |
|---|---|
| Routing | `api-gateway/application.yml` routes section |
| Offloading (auth) | `api-gateway/AuthenticationFilter.java` |
| Offloading (rate limit) | `api-gateway/application.yml` RequestRateLimiter filter |
| Offloading (resilience) | `api-gateway/application.yml` CircuitBreaker/Retry filters |
| Aggregation | `api-gateway-subpatterns/AggregationController.java` (this folder) |
| Offloading (caching/compression) | `api-gateway-subpatterns/application-offloading.yml` (this folder) |