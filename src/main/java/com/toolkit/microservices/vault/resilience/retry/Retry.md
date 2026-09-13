# Retry Plain Java Example

Demonstrates the **RETRY** pattern from first principles — no external libraries. Understanding this manual version
makes it much easier to understand what Resilience4j's `@Retry` annotation is doing under the hood.

## The Problem

Network calls / downstream services fail transiently (blips, timeouts, temporary overload). Failing the whole request
immediately is wasteful when a second attempt might just succeed.

## The Pattern

Re-attempt a failed operation a bounded number of times, usually with a delay between attempts (fixed, or exponential
backoff) so you don't hammer an already-struggling service.

## When to Use

- Only for **transient** failures (network blips, timeouts, 5xx errors)
- **Never** retry on business/validation errors (400s) — retrying won't fix "invalid input," it'll just waste time and
  resources
- Be careful retrying **non-idempotent** operations (e.g. "charge card", "send email") — a retry could cause the side
  effect to happen twice