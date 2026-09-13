# Circuit Breaker Plain Java Example

Demonstrates the **CIRCUIT BREAKER** pattern from first principles.

## The Problem

If a downstream service is struggling or down, retrying (see Retry pattern) still sends traffic its way — which can make
things **worse** (pile-on effect) and wastes the caller's time/threads waiting on calls that are very likely to fail
anyway.

## The Pattern

Track recent call outcomes. If the failure rate crosses a threshold, "open" the circuit — stop calling the downstream
service entirely for a cooldown period, and fail fast instead. After the cooldown, allow a few test calls through
(`HALF_OPEN`) to see if the service has recovered.

## The Three States

```
CLOSED -----(failure rate > threshold)-----> OPEN
   ^                                            |
   |                                    (wait duration elapses)
   |                                            v
   +----(test calls succeed)---------------HALF_OPEN
                                                 |
                                       (test calls fail)
                                                 v
                                               OPEN
```

- **CLOSED**: normal operation, calls pass through, failures are tracked
- **OPEN**: calls fail immediately without hitting the downstream service
- **HALF_OPEN**: a limited number of trial calls are let through to test if the downstream service has recovered

## Retry vs Circuit Breaker

Often used **together**:

- **Retry**: "try this ONE call again, it might have been a blip"
- **Circuit Breaker**: "this service has been failing a LOT, stop even trying for a while, protect ourselves and give it
  time to recover"