# Bulkhead Plain Java Example

Demonstrates the **BULKHEAD** pattern from first principles.

## The Name

Borrowed from ship design — a ship's hull is divided into watertight compartments (bulkheads) so that if one section
floods, the whole ship doesn't sink. Damage is contained.

## The Problem

If **all** downstream calls share the same thread pool / connection pool, a single slow or failing dependency can
exhaust every thread waiting on it — starving calls to **other**, perfectly healthy dependencies too. This is sometimes
called "resource exhaustion cascading failure."

## The Pattern

Give each dependency (or category of work) its **own isolated pool** of resources (threads, connections, semaphore
permits). If Dependency A is slow/stuck, it can only exhaust *its own* pool — calls to Dependency B keep flowing
normally through B's separate pool.

## Two Common Implementations

1. **Thread Pool Bulkhead** — each dependency gets its own dedicated `ExecutorService` with a fixed number of threads.
2. **Semaphore Bulkhead** — lighter weight; limits concurrent calls via permits, without dedicating actual OS threads
   per dependency.

This example demonstrates **both**, showing how isolating one flaky dependency protects a healthy one from being
starved.