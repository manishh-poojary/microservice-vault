# Microservices Vault

A Spring Boot learning project focused on resilience patterns used in microservices and distributed systems.

This repository demonstrates how to build more fault-tolerant services using patterns such as:

- Retry
- Circuit Breaker
- Rate Limiting
- Timeouts
- Bulkhead Isolation

It is designed as a practical reference and hands-on playground for understanding how these patterns behave in
real-world service-to-service communication.

## Tech Stack

- Java 17
- Spring Boot 4.1.1
- Maven
- Spring Web MVC
- SpringDoc OpenAPI
- Spring Cloud Circuit Breaker / Resilience4j
- Spring Boot Actuator

## Project Overview

The application is organized around the `com.toolkit.microservices.vault.resilience` package and includes several
implementation examples for resilience strategies:

- `retry/` — retry logic with backoff and simulated transient failures
- `circuitbreaker/` — circuit breaker state transitions and fallback handling
- `ratelimiter/` — fixed-window and token-bucket rate limiting examples
- `timeout/` — timeout logic for slow dependencies
- `bulkhead/` — semaphore and thread-pool isolation patterns

The project also contains a Spring Boot configuration file with Resilience4j examples for:

- retry
- circuit breakers
- bulkheads
- time limiters
- rate limiters

## Project Structure

```text
microservices-vault/
├── .mvn/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/toolkit/microservices/vault/
│   │   │       ├── MicroservicesVaultApplication.java
│   │   │       └── resilience/
│   │   │           ├── bulkhead/
│   │   │           ├── circuitbreaker/
│   │   │           ├── ratelimiter/
│   │   │           ├── retry/
│   │   │           └── timeout/
│   │   └── resources/
│   │       ├── application.properties
│   │       └── application.yaml
│   └── test/
│       └── java/com/toolkit/microservices/vault/
├── .gitignore
├── HELP.md
├── mvnw
├── mvnw.cmd
├── pom.xml
├── README.md
└── target/
```

## Prerequisites

Before running the project, make sure you have:

- Java 17+
- Maven, or the provided wrapper scripts (`mvnw` / `mvnw.cmd`)
- A terminal or IDE such as IntelliJ IDEA or VS Code

## Getting Started

Clone the repository:

```bash
git clone <your-repo-url>
cd microservices-vault
```

Run the application:

```bash
./mvnw spring-boot:run
```

On Windows:

```bash
mvnw.cmd spring-boot:run
```

The app starts with Spring Boot and loads the resilience configuration from `src/main/resources/application.yaml`.

## API and Swagger UI

This project includes SpringDoc OpenAPI support. Once the app is running, you can access the documentation here:

```text
http://localhost:8080/swagger-ui/index.html
```

## What This Repository Demonstrates

This repo shows how to:

- retry transient failures using backoff
- protect services from repeated failing calls
- reject excessive traffic when a dependency is overloaded
- fail fast for slow downstream services
- isolate high-risk operations using thread or semaphore boundaries
- recover gracefully after a dependency becomes healthy again

## Why It Matters

In distributed systems, dependencies can fail, slow down, or become overloaded. Resilience patterns help systems:

- stay available under failure
- reduce cascading dependency failures
- protect shared resources and downstream services
- degrade gracefully instead of failing completely

## Notes

This project is primarily a learning and demonstration repository. It is not a production-ready microservice system, but
it provides a clear foundation for understanding core resilience concepts.

## License

This project does not currently include a specific license file. If it is shared publicly or published, consider adding
an appropriate license before distribution.

## Contributing

Contributions and improvements are welcome. You can extend this project by adding:

- OpenFeign clients
- database-backed examples
- API gateway patterns
- Kafka/RabbitMQ integration
- Docker Compose setup
- more realistic service-to-service scenarios
