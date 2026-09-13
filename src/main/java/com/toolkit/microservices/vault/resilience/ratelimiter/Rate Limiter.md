# Rate Limiter Plain Java Example

Demonstrates the **RATE LIMITER** pattern from first principles.

## The Problem

Without limits, a client (or a buggy retry loop, or a traffic spike) can send more requests than a service can safely
handle, degrading performance for everyone or causing outright failure. Rate limiting protects a service from being
overwhelmed — either by external clients calling **your** service, or by **your** service calling a downstream
dependency that has its own capacity limits (e.g. a third-party API with a "100 requests/minute" quota).

## Rate Limiter vs Bulkhead (easy to confuse)

- **Bulkhead** limits **concurrent** calls happening at the same time (a snapshot of "how many calls are in flight right
  now")
- **Rate Limiter** limits calls **per time window** regardless of concurrency (e.g. "100 calls per minute" even if
  they're not concurrent at all — 10 calls per second for 10 seconds still hits a 100/min cap)

## Two Classic Algorithms Shown Here

### 1. Fixed Window Counter

Simplest approach: count requests in a fixed time window (e.g. this calendar minute), reset the counter each window.

**Downside**: bursts at window boundaries (e.g. 100 requests in the last 1ms of one window + 100 in the first 1ms of the
next = 200 requests in ~2ms, even though the "rate" is nominally 100/window).

### 2. Token Bucket

A bucket holds up to N tokens. Each request consumes one token. Tokens refill continuously at a fixed rate. If the
bucket is empty, the request is rejected (or queued).

This is what most production rate limiters (including Resilience4j) actually use — it allows brief bursts up to the
bucket size while still enforcing a long-term average rate.