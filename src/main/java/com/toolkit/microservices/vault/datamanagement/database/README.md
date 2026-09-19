# Database per Service

## The problem with a shared database

Early microservices projects often start with ONE database that every service reads/writes to directly. This feels
convenient at first, but it quietly recreates a monolith at the data layer:

- Any service can change any table, so nobody can safely change a schema without checking every other service that might
  touch it
- Services become coupled through the database instead of through APIs - the actual "interface" between two services
  becomes "whatever rows I expect to find," which is invisible and unversioned
- One service running a slow query or holding locks can degrade every OTHER service sharing that database
- You lose the ability to pick the right storage technology per service (e.g. a search service wanting Elasticsearch, an
  order service wanting a relational DB with strong consistency)

## The pattern

Each microservice owns its own database. No other service is allowed to access it directly — not even for a read. All
access goes through that service's API.

```
Order Service     ──► orders_db      (only Order Service touches this)
Inventory Service ──► inventory_db   (only Inventory Service touches this)
Customer Service  ──► customer_db    (only Customer Service touches this)
```

This is what makes **Database per Service** the foundational pattern underneath almost everything else you've built in
this repo:

| Pattern already in this repo                | Why it exists BECAUSE of Database per Service                                                                                                                                     |
|---------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Saga** (choreography/orchestration)       | No single database transaction can span services anymore — Saga is how you keep data consistent across separately-owned databases                                                 |
| **CQRS**                                    | The read model is itself a "private database" the query side owns, kept in sync via events — same ownership principle, applied to reads                                           |
| **API Gateway Aggregation**                 | A client needing data from 2 services can't just JOIN across their databases (they're different databases entirely) — aggregation at the gateway is the replacement for that JOIN |
| **Event-driven messaging (Kafka/RabbitMQ)** | The mechanism services use to tell each other "my data changed" without either one reading the other's database directly                                                          |

If you remember only one sentence from this whole set of examples: **most of the complexity in microservices patterns
exists specifically to compensate for what a shared database used to give you for free**
(consistency, joins, single transactions) — Database per Service is the decision that creates that complexity, in
exchange for real independence.

## What you gain

- Each service can be deployed, scaled, and changed independently — a schema migration in Inventory Service can't break
  Order Service, because Order Service has no idea Inventory Service's schema even exists
- Each service can use the RIGHT database technology for its own job (polyglot persistence) — shown in the
  `docker-compose.yml` here:
  Postgres for Order Service (needs strong consistency for financial data), MongoDB for Catalog Service (flexible schema
  for varied product attributes), Redis for Session Service (pure speed, ephemeral data)
- Failure isolation: one service's database having issues doesn't directly break every other service

## What it costs you (and where to look for the fix)

- **No cross-service JOINs** → API Composition (aggregate at the caller, e.g. the Gateway Aggregation pattern) or CQRS
  (build a denormalized view ahead of time instead of joining on read)
- **No cross-service transactions** → Saga pattern
- **Data duplication** → normal and expected. Order Service might store a customer's NAME even though Customer Service
  is the actual owner of customer data — this is deliberate denormalization for availability/performance, kept
  eventually consistent via events, not a mistake to "fix" by adding a foreign key across databases (which is impossible
  anyway — see `application-order-service.yml`'s comment).

## A common middle-ground worth knowing

**Schema per service** (one database SERVER, but a separate schema/ namespace per service, with DB-level permissions
preventing cross-schema access) is a lighter-weight variant some teams use, especially early on or for cost reasons. It
keeps ownership boundaries enforced at the permission level while reducing operational overhead (one DB instance to run,
not N). It's a reasonable stepping stone, though it doesn't give you the polyglot-persistence or failure-isolation
benefits of fully separate database instances.