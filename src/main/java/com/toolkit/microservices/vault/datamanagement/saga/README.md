# Saga Pattern — The Problem It Solves

## Why distributed transactions are hard

In a monolith with one database, "place an order" is a single ACID transaction: reserve stock, charge payment, create
shipment — all commit together, or all roll back together. The database guarantees it.

In microservices with **Database per Service**, that guarantee is gone. Order Service, Inventory Service, and Payment
Service each own a separate database. There is no shared transaction spanning all three.

Two-phase commit (2PC/XA) technically exists, but is generally avoided in microservices: it requires all participants to
hold locks until every participant is ready, which destroys availability (one slow service blocks everyone) and doesn't
work across services that don't support XA — like most third-party payment APIs.

## The Saga pattern

Break the distributed transaction into a **sequence of local transactions**. Each service commits its own local
transaction and publishes an event (or receives a command) that triggers the next step.

If a step fails, run **compensating transactions** to undo the previously committed steps — semantically reversing them,
since you can't "roll back"
something that has already committed.

```
Happy path:      Create Order → Reserve Stock → Charge Payment → Confirm Order
Failure path:    Create Order → Reserve Stock → Payment FAILS
                                     ↓
                 Cancel Order ← Release Stock       (compensating transactions)
```

## Critical properties of a Saga

**No isolation.** Unlike an ACID transaction, intermediate states ARE visible to other parts of the system. Between
"stock reserved" and
"payment charged", another request can see that stock as reserved. Your domain model must tolerate this (e.g. an order
in a `PENDING` state).

**Compensating transactions are semantic, not literal.** You don't
"un-charge" a card — you issue a refund. You don't delete an order — you mark it `CANCELLED`. The audit trail of both
actions remains.

**Some steps can't be compensated.** "Send confirmation email" can't be unsent. Order your saga so irreversible steps
happen LAST, after everything reversible has already succeeded.

**Every step must be idempotent.** Messages get redelivered (at-least-once delivery, as covered in the Kafka/RabbitMQ
examples). Reserving stock twice for the same order must not decrement twice.

## Two implementation styles

|                  | Choreography                                        | Orchestration                                            |
|------------------|-----------------------------------------------------|----------------------------------------------------------|
| **Coordination** | None — services react to each other's events        | A central orchestrator issues commands                   |
| **Coupling**     | Services coupled to event contracts                 | Services coupled to the orchestrator                     |
| **Visibility**   | Hard — logic is scattered across services           | Easy — the whole flow is in one class                    |
| **Best for**     | Simple sagas (2-4 steps)                            | Complex sagas, or when you need clear visibility/control |
| **Risk**         | Cyclic dependencies, hard to debug "what happened?" | Orchestrator becomes a bottleneck or a god-object        |

Both are implemented in this repo — `saga-choreography/` and
`saga-orchestration/` — using the same order-placement scenario so you can compare them directly.

# Choreography vs Orchestration — Side by Side

Both folders implement the SAME order-placement saga so they can be compared directly.

## The same failure, handled two ways

**Choreography** (payment declined):

```
Order Service      publishes OrderCreated
Inventory Service  hears it, reserves stock, publishes StockReserved
Payment Service    hears it, declines, publishes PaymentFailed
Inventory Service  hears it, releases stock, publishes StockReleased
Order Service      hears it, cancels the order
```

No one is "in charge." Each service only knows its own reactions.

**Orchestration** (payment declined):

```
Orchestrator  calls Order Service    -> order created
Orchestrator  calls Inventory        -> stock reserved
Orchestrator  calls Payment          -> FAILS
Orchestrator  unwinds its compensation stack (LIFO):
                 -> Inventory.release()
                 -> Order.cancel()
```

The orchestrator owns the whole flow.

## Choosing between them

**Use Choreography when:**

- The saga is short (2–4 steps)
- Services are genuinely independent and you want maximum decoupling
- You're already event-driven and want to add participants without changing existing services

**Use Orchestration when:**

- The saga has many steps, branches, or conditional logic
- You need to see/monitor/debug the business process as a whole
- Compensation ordering is complex
- Non-technical stakeholders need to understand the flow

**A practical heuristic:** if you can't answer "what happens when step 3 fails?" by reading one file, you probably want
orchestration.

## Shared concerns regardless of style

| Concern                     | Why it matters                                                                                                          |
|-----------------------------|-------------------------------------------------------------------------------------------------------------------------|
| **Idempotency**             | At-least-once delivery means redelivery is normal. Every step and compensation must be safe to run twice.               |
| **Dual-write problem**      | DB write + event publish aren't atomic. Use the Transactional Outbox pattern.                                           |
| **Saga state persistence**  | Orchestrators must survive a crash mid-saga. Choreography relies on the broker retaining events.                        |
| **Failed compensations**    | The genuine worst case — the system cannot self-heal. Alert and queue for manual intervention.                          |
| **No isolation**            | Intermediate states are visible. Model them explicitly (`PENDING`, `RESERVED`) rather than pretending they don't exist. |
| **Irreversible steps last** | You can't unsend an email or un-ship a package. Order steps so reversible ones fail first.                              |

## When to reach for a framework

Hand-rolling works for simple sagas. Once you need timeouts, retries, versioned saga definitions, and visual monitoring,
consider **Temporal**, **Camunda**, **Axon Framework**, or **Eventuate Tram Saga** — they solve the
persistence/recovery/retry plumbing that's easy to get subtly wrong.