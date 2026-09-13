# Timeout Plain Java Example

Demonstrates the **TIMEOUT** pattern from first principles.

## The Problem

Without a timeout, a call to a slow/hung downstream service can block the caller **indefinitely**. This ties up threads,
connections, and other resources — and if enough calls pile up waiting forever, the caller itself becomes unresponsive
(this is often the root cause behind cascading failures across a whole system).

## The Pattern

Bound how long you're willing to wait for a response. If the operation doesn't complete within that time, give up, free
the resources, and handle it as a failure (fallback, error, or trigger a retry).

## Why This Matters Even More in Microservices

A request in a microservice architecture often fans out across several downstream calls. If Service A calls B, which
calls C, and C hangs forever, that hang propagates all the way back up unless **every hop** enforces its own timeout.

## Important Distinction

- **Connection timeout**: how long to wait to *establish* a connection
- **Read/response timeout**: how long to wait for a *response* once connected

Both matter and are usually configured separately in real HTTP clients.

## Note on `Thread.interrupt()`

Timing out a thread doesn't magically stop it — Java has no safe way to forcibly kill a thread. Interrupting only works
if the running code actually checks `Thread.interrupted()` or throws `InterruptedException` from a blocking call
(`sleep`, `wait`, blocking I/O). A tight CPU-bound loop that never checks interruption will keep running even after
"timing out" from the caller's perspective — the caller just stops **waiting** for it, it doesn't stop the runaway
thread itself.