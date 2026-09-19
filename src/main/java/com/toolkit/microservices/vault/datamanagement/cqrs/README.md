# CQRS — Command Query Responsibility Segregation

## The problem

A single data model tries to serve two very different needs:

- **Writes** want a normalized, consistent shape that enforces business rules and invariants (e.g. an `Order` with a
  foreign key to `Customer`, and a separate `OrderItems` table).
- **Reads** often want a completely different, denormalized shape - "give me this order WITH the customer's name, WITH
  each item's product name and current price, WITH the running total" - which in a normalized model means joining 4-5
  tables on every single read.

As an app grows, these two needs pull the data model in opposite directions. Indexes that help reads slow down writes. A
schema normalized enough for safe writes is awkward and slow for the read patterns the UI actually needs.

## The pattern

Split the model in two:

- **Command side** — handles writes. Enforces invariants, runs business logic, is the source of truth. Optimized for
  correctness and write throughput.
- **Query side** — handles reads. A denormalized, often heavily duplicated view built specifically to answer the actual
  questions the application asks, with no joins needed. Optimized for read speed.

The two sides are kept in sync via events: every command that changes state publishes an event; the query side listens
and updates its own denormalized copy accordingly.

```
Write:  Client -> Command -> Write Model (normalized) -> publishes Event
                                                                |
Read:                                    Query Model (denormalized) <- Event Handler
        Client <- Query <- Query Model
```

## The trade-off that matters most: eventual consistency

The query side updates ASYNCHRONOUSLY, after the event arrives. There is a window - usually milliseconds, but non-zero -
where a write has succeeded but the read model doesn't reflect it yet. If a client writes then immediately reads, it
might see stale data.

This is the single biggest thing CQRS costs you, and the single biggest reason NOT to reach for it by default. It solves
a real scaling/complexity problem, but it introduces a subtler one everywhere in exchange.

## When CQRS earns its complexity

- Read and write loads are wildly asymmetric (e.g. 1000 reads for every write - a product catalog, a social feed)
- The read shape the UI wants is genuinely far from the write model's natural shape (dashboards, reports, search results
  combining many entities)
- You want to scale reads and writes independently (different DB instances, different tech entirely - e.g. writes to
  Postgres, reads from Elasticsearch)

## When to skip it

Most CRUD services. If your reads and writes are roughly symmetric and the read shape isn't far from the write shape,
CQRS just adds eventual consistency and two things to keep in sync for no real benefit. This is a commonly over-applied
pattern - "we're a microservice architecture, so we should use CQRS everywhere" is a trap. Reach for it when the
specific problem it solves is the problem you actually have.

## CQRS does NOT require Event Sourcing (common misconception)

CQRS is about splitting read/write MODELS. Event Sourcing is about storing state as a sequence of events instead of
current-state rows. They compose beautifully together (Event Sourcing naturally produces the event stream CQRS's query
side needs to consume) but are independent decisions. You can do CQRS with two ordinary databases and no event store at
all - which is exactly what this example does. 