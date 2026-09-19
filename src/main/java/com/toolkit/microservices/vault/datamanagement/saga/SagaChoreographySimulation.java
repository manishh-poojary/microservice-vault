package com.toolkit.microservices.vault.datamanagement.saga;

import java.util.*;
import java.util.function.Consumer;

/**
 * SagaChoreographySimulation
 * ------------------------------
 * A RUNNABLE, dependency-free simulation of the Choreography-based Saga.
 * <p>
 * There is NO central coordinator here. Each service subscribes to events
 * and independently decides what to do next, publishing its own events in
 * turn. The "saga" is an emergent property of these reactions - no single
 * place in the code describes the whole flow, which is exactly the
 * defining trade-off of choreography (flexible and decoupled, but the
 * business process is invisible in any one file).
 * <p>
 * The in-memory EventBus below stands in for Kafka/RabbitMQ. In a real
 * system each @EventListener here would be a @KafkaListener in a
 * SEPARATE, independently deployed service with its OWN database.
 * <p>
 * HAPPY PATH:
 * OrderCreated -> StockReserved -> PaymentCompleted -> OrderConfirmed
 * <p>
 * FAILURE PATH (payment declined):
 * OrderCreated -> StockReserved -> PaymentFailed
 * |
 * (compensation) v
 * StockReleased -> OrderCancelled
 */
public class SagaChoreographySimulation {

    // ===================== Events =====================
    sealed interface Event permits OrderCreated, StockReserved, StockReservationFailed,
            PaymentCompleted, PaymentFailed, StockReleased, OrderConfirmed, OrderCancelled {
    }

    record OrderCreated(String orderId, String productId, int quantity, double amount) implements Event {
    }

    record StockReserved(String orderId, String productId, int quantity, double amount) implements Event {
    }

    record StockReservationFailed(String orderId, String reason) implements Event {
    }

    record PaymentCompleted(String orderId, String transactionId) implements Event {
    }

    record PaymentFailed(String orderId, String productId, int quantity, String reason) implements Event {
    }

    record StockReleased(String orderId, String reason) implements Event {
    }

    record OrderConfirmed(String orderId) implements Event {
    }

    record OrderCancelled(String orderId, String reason) implements Event {
    }

    // ===================== Event Bus (stands in for Kafka) =====================
    static class EventBus {
        private final Map<Class<?>, List<Consumer<Event>>> subscribers = new HashMap<>();

        <T extends Event> void subscribe(Class<T> type, Consumer<Event> handler) {
            subscribers.computeIfAbsent(type, k -> new ArrayList<>()).add(handler);
        }

        void publish(Event event) {
            System.out.println("  [EVENT] " + event.getClass().getSimpleName() + " " + event);
            subscribers.getOrDefault(event.getClass(), List.of())
                    .forEach(handler -> handler.accept(event));
        }
    }

    // ===================== Order Service =====================
    static class OrderService {
        private final EventBus bus;
        private final Map<String, String> orderStatus = new HashMap<>(); // orderId -> status

        OrderService(EventBus bus) {
            this.bus = bus;
            // Reacts to the OUTCOME events from other services
            bus.subscribe(PaymentCompleted.class, e -> onPaymentCompleted((PaymentCompleted) e));
            bus.subscribe(StockReleased.class, e -> onStockReleased((StockReleased) e));
            bus.subscribe(StockReservationFailed.class, e -> onStockReservationFailed((StockReservationFailed) e));
        }

        void placeOrder(String orderId, String productId, int quantity, double amount) {
            // LOCAL TRANSACTION: persist the order in PENDING state and publish.
            // Note the order is visible to the rest of the system in this
            // intermediate state - sagas have NO isolation.
            orderStatus.put(orderId, "PENDING");
            System.out.println("[OrderService] Order " + orderId + " created with status PENDING");
            bus.publish(new OrderCreated(orderId, productId, quantity, amount));
        }

        void onPaymentCompleted(PaymentCompleted e) {
            orderStatus.put(e.orderId(), "CONFIRMED");
            System.out.println("[OrderService] Order " + e.orderId() + " -> CONFIRMED");
            bus.publish(new OrderConfirmed(e.orderId()));
        }

        // COMPENSATING TRANSACTION: semantically cancel, don't delete.
        void onStockReleased(StockReleased e) {
            orderStatus.put(e.orderId(), "CANCELLED");
            System.out.println("[OrderService] COMPENSATION: Order " + e.orderId() + " -> CANCELLED (" + e.reason() + ")");
            bus.publish(new OrderCancelled(e.orderId(), e.reason()));
        }

        void onStockReservationFailed(StockReservationFailed e) {
            orderStatus.put(e.orderId(), "CANCELLED");
            System.out.println("[OrderService] COMPENSATION: Order " + e.orderId() + " -> CANCELLED (" + e.reason() + ")");
            bus.publish(new OrderCancelled(e.orderId(), e.reason()));
        }

        String getStatus(String orderId) {
            return orderStatus.get(orderId);
        }
    }

    // ===================== Inventory Service =====================
    static class InventoryService {
        private final EventBus bus;
        private final Map<String, Integer> stock = new HashMap<>();
        private final Set<String> processedOrders = new HashSet<>(); // IDEMPOTENCY guard

        InventoryService(EventBus bus, Map<String, Integer> initialStock) {
            this.bus = bus;
            this.stock.putAll(initialStock);
            bus.subscribe(OrderCreated.class, e -> onOrderCreated((OrderCreated) e));
            bus.subscribe(PaymentFailed.class, e -> onPaymentFailed((PaymentFailed) e));
        }

        void onOrderCreated(OrderCreated e) {
            // IDEMPOTENCY: at-least-once delivery means this event CAN be
            // redelivered. Without this guard, a redelivery would reserve
            // stock twice for the same order.
            if (!processedOrders.add("reserve:" + e.orderId())) {
                System.out.println("[InventoryService] Duplicate OrderCreated for " + e.orderId() + " - IGNORED (idempotency)");
                return;
            }

            int available = stock.getOrDefault(e.productId(), 0);
            if (available < e.quantity()) {
                System.out.println("[InventoryService] Insufficient stock for " + e.productId()
                        + " (need " + e.quantity() + ", have " + available + ")");
                bus.publish(new StockReservationFailed(e.orderId(), "Insufficient stock"));
                return;
            }

            stock.put(e.productId(), available - e.quantity()); // LOCAL TRANSACTION
            System.out.println("[InventoryService] Reserved " + e.quantity() + " of " + e.productId()
                    + " (remaining: " + stock.get(e.productId()) + ")");
            bus.publish(new StockReserved(e.orderId(), e.productId(), e.quantity(), e.amount()));
        }

        // COMPENSATING TRANSACTION: give the stock back.
        void onPaymentFailed(PaymentFailed e) {
            if (!processedOrders.add("release:" + e.orderId())) {
                System.out.println("[InventoryService] Duplicate PaymentFailed for " + e.orderId() + " - IGNORED (idempotency)");
                return;
            }
            stock.merge(e.productId(), e.quantity(), Integer::sum);
            System.out.println("[InventoryService] COMPENSATION: Released " + e.quantity() + " of " + e.productId()
                    + " (restored to: " + stock.get(e.productId()) + ")");
            bus.publish(new StockReleased(e.orderId(), e.reason()));
        }

        int getStock(String productId) {
            return stock.getOrDefault(productId, 0);
        }
    }

    // ===================== Payment Service =====================
    static class PaymentService {
        private final EventBus bus;
        private final double creditLimit;
        private final Set<String> processedOrders = new HashSet<>();

        PaymentService(EventBus bus, double creditLimit) {
            this.bus = bus;
            this.creditLimit = creditLimit;
            bus.subscribe(StockReserved.class, e -> onStockReserved((StockReserved) e));
        }

        void onStockReserved(StockReserved e) {
            if (!processedOrders.add(e.orderId())) {
                System.out.println("[PaymentService] Duplicate StockReserved for " + e.orderId() + " - IGNORED (idempotency)");
                return;
            }

            if (e.amount() > creditLimit) {
                System.out.println("[PaymentService] Payment DECLINED for order " + e.orderId()
                        + " (amount " + e.amount() + " exceeds limit " + creditLimit + ")");
                // Publishing the failure carries the productId/quantity so
                // Inventory knows WHAT to compensate - the event must carry
                // enough context for compensators to act without callbacks.
                bus.publish(new PaymentFailed(e.orderId(), e.productId(), e.quantity(), "Payment declined"));
                return;
            }

            String txId = "TX-" + UUID.randomUUID().toString().substring(0, 8);
            System.out.println("[PaymentService] Charged " + e.amount() + " for order " + e.orderId() + " (" + txId + ")");
            bus.publish(new PaymentCompleted(e.orderId(), txId));
        }
    }

    // ===================== Simulation =====================
    public static void main(String[] args) {

        System.out.println("################ SCENARIO 1: HAPPY PATH ################\n");
        EventBus bus1 = new EventBus();
        OrderService orders1 = new OrderService(bus1);
        InventoryService inventory1 = new InventoryService(bus1, Map.of("LAPTOP-01", 10));
        new PaymentService(bus1, 5000.0);

        orders1.placeOrder("ORDER-1", "LAPTOP-01", 2, 2400.0);
        System.out.println("\nRESULT: Order status = " + orders1.getStatus("ORDER-1")
                + ", remaining stock = " + inventory1.getStock("LAPTOP-01"));

        System.out.println("\n\n########## SCENARIO 2: PAYMENT FAILS -> COMPENSATION ##########\n");
        EventBus bus2 = new EventBus();
        OrderService orders2 = new OrderService(bus2);
        InventoryService inventory2 = new InventoryService(bus2, Map.of("LAPTOP-01", 10));
        new PaymentService(bus2, 5000.0); // credit limit 5000

        orders2.placeOrder("ORDER-2", "LAPTOP-01", 3, 7200.0); // exceeds limit -> payment fails
        System.out.println("\nRESULT: Order status = " + orders2.getStatus("ORDER-2")
                + ", stock restored to = " + inventory2.getStock("LAPTOP-01")
                + " (should be back to 10 after compensation)");

        System.out.println("\n\n######### SCENARIO 3: INSUFFICIENT STOCK (fails early) #########\n");
        EventBus bus3 = new EventBus();
        OrderService orders3 = new OrderService(bus3);
        InventoryService inventory3 = new InventoryService(bus3, Map.of("LAPTOP-01", 1));
        new PaymentService(bus3, 5000.0);

        orders3.placeOrder("ORDER-3", "LAPTOP-01", 5, 1000.0); // only 1 in stock
        System.out.println("\nRESULT: Order status = " + orders3.getStatus("ORDER-3")
                + ", stock untouched = " + inventory3.getStock("LAPTOP-01")
                + " (no compensation needed - failed before any stock was reserved)");

        System.out.println("\n\n########## SCENARIO 4: DUPLICATE EVENT (idempotency) ##########\n");
        EventBus bus4 = new EventBus();
        OrderService orders4 = new OrderService(bus4);
        InventoryService inventory4 = new InventoryService(bus4, Map.of("LAPTOP-01", 10));
        new PaymentService(bus4, 5000.0);

        orders4.placeOrder("ORDER-4", "LAPTOP-01", 2, 2400.0);
        System.out.println("\n--- Simulating Kafka redelivering the SAME OrderCreated event ---");
        bus4.publish(new OrderCreated("ORDER-4", "LAPTOP-01", 2, 2400.0));
        System.out.println("\nRESULT: stock = " + inventory4.getStock("LAPTOP-01")
                + " (should be 8, NOT 6 - the duplicate did not double-reserve)");
    }
}
