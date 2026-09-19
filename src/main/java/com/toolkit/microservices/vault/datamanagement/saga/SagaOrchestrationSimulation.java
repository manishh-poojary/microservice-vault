package com.toolkit.microservices.vault.datamanagement.saga;

import java.util.*;

/**
 * SagaOrchestrationSimulation
 * -------------------------------
 * A RUNNABLE, dependency-free simulation of the Orchestration-based Saga.
 * <p>
 * CONTRAST WITH CHOREOGRAPHY:
 * In choreography, no single file described the order-placement flow -
 * it emerged from services reacting to each other. Here, the entire
 * business process is explicitly visible in ONE place
 * (OrderSagaOrchestrator.execute()). You can read it top to bottom and
 * understand exactly what happens, in what order, and what gets undone
 * on failure.
 * <p>
 * The cost: participants are now coupled to the orchestrator, and the
 * orchestrator must know about every participant. It risks becoming a
 * god-object if the saga grows unchecked.
 * <p>
 * KEY IMPLEMENTATION DETAIL - THE COMPENSATION STACK:
 * As each step succeeds, its compensating action is pushed onto a stack.
 * On failure, the stack is unwound in REVERSE order (LIFO). This matters:
 * compensations must run in the opposite order to the forward steps,
 * just like unwinding nested transactions.
 */
public class SagaOrchestrationSimulation {

    // ===================== Saga step abstraction =====================
    interface SagaStep {
        String name();

        void execute(SagaContext ctx) throws SagaStepFailedException;

        void compensate(SagaContext ctx);
    }

    static class SagaStepFailedException extends Exception {
        SagaStepFailedException(String msg) {
            super(msg);
        }
    }

    /**
     * Carries state between steps - each step reads/writes what it needs.
     */
    static class SagaContext {
        final String orderId, productId;
        final int quantity;
        final double amount;
        String transactionId;
        String finalStatus = "UNKNOWN";

        SagaContext(String orderId, String productId, int quantity, double amount) {
            this.orderId = orderId;
            this.productId = productId;
            this.quantity = quantity;
            this.amount = amount;
        }
    }

    // ===================== Participant services =====================
    static class OrderService {
        final Map<String, String> statuses = new HashMap<>();

        void create(String orderId) {
            statuses.put(orderId, "PENDING");
            System.out.println("    [OrderService] Order " + orderId + " created (PENDING)");
        }

        void confirm(String orderId) {
            statuses.put(orderId, "CONFIRMED");
            System.out.println("    [OrderService] Order " + orderId + " CONFIRMED");
        }

        void cancel(String orderId) {
            statuses.put(orderId, "CANCELLED");
            System.out.println("    [OrderService] COMPENSATE: Order " + orderId + " CANCELLED");
        }

        String status(String orderId) {
            return statuses.get(orderId);
        }
    }

    static class InventoryService {
        final Map<String, Integer> stock = new HashMap<>();

        InventoryService(Map<String, Integer> initial) {
            stock.putAll(initial);
        }

        void reserve(String productId, int qty) throws SagaStepFailedException {
            int available = stock.getOrDefault(productId, 0);
            if (available < qty) {
                throw new SagaStepFailedException("Insufficient stock: need " + qty + ", have " + available);
            }
            stock.put(productId, available - qty);
            System.out.println("    [InventoryService] Reserved " + qty + " of " + productId
                    + " (remaining: " + stock.get(productId) + ")");
        }

        void release(String productId, int qty) {
            stock.merge(productId, qty, Integer::sum);
            System.out.println("    [InventoryService] COMPENSATE: Released " + qty + " of " + productId
                    + " (restored to: " + stock.get(productId) + ")");
        }

        int get(String productId) {
            return stock.getOrDefault(productId, 0);
        }
    }

    static class PaymentService {
        final double creditLimit;
        final List<String> refunds = new ArrayList<>();

        PaymentService(double creditLimit) {
            this.creditLimit = creditLimit;
        }

        String charge(String orderId, double amount) throws SagaStepFailedException {
            if (amount > creditLimit) {
                throw new SagaStepFailedException("Payment declined: " + amount + " exceeds limit " + creditLimit);
            }
            String txId = "TX-" + UUID.randomUUID().toString().substring(0, 8);
            System.out.println("    [PaymentService] Charged " + amount + " (" + txId + ")");
            return txId;
        }

        void refund(String txId) {
            refunds.add(txId);
            // Semantic compensation: you don't "un-charge" a card, you refund it.
            // Both the charge AND the refund remain in the audit trail.
            System.out.println("    [PaymentService] COMPENSATE: Refunded " + txId);
        }
    }

    static class ShippingService {
        final List<String> shipments = new ArrayList<>();

        void schedule(String orderId) throws SagaStepFailedException {
            if (orderId.endsWith("-FAILSHIP")) {
                throw new SagaStepFailedException("No delivery slots available");
            }
            shipments.add(orderId);
            System.out.println("    [ShippingService] Shipment scheduled for " + orderId);
        }

        void cancelShipment(String orderId) {
            shipments.remove(orderId);
            System.out.println("    [ShippingService] COMPENSATE: Shipment cancelled for " + orderId);
        }
    }

    // ===================== The Orchestrator =====================
    static class OrderSagaOrchestrator {
        private final List<SagaStep> steps;

        OrderSagaOrchestrator(OrderService orderService, InventoryService inventory,
                              PaymentService payment, ShippingService shipping) {
            // THE ENTIRE BUSINESS PROCESS, VISIBLE IN ONE PLACE.
            // Ordering matters: irreversible or hard-to-compensate steps
            // (shipping) go LAST, after everything reversible has succeeded.
            this.steps = List.of(
                    new SagaStep() {
                        public String name() {
                            return "CreateOrder";
                        }

                        public void execute(SagaContext ctx) {
                            orderService.create(ctx.orderId);
                        }

                        public void compensate(SagaContext ctx) {
                            orderService.cancel(ctx.orderId);
                        }
                    },
                    new SagaStep() {
                        public String name() {
                            return "ReserveStock";
                        }

                        public void execute(SagaContext ctx) throws SagaStepFailedException {
                            inventory.reserve(ctx.productId, ctx.quantity);
                        }

                        public void compensate(SagaContext ctx) {
                            inventory.release(ctx.productId, ctx.quantity);
                        }
                    },
                    new SagaStep() {
                        public String name() {
                            return "ChargePayment";
                        }

                        public void execute(SagaContext ctx) throws SagaStepFailedException {
                            ctx.transactionId = payment.charge(ctx.orderId, ctx.amount);
                        }

                        public void compensate(SagaContext ctx) {
                            if (ctx.transactionId != null) payment.refund(ctx.transactionId);
                        }
                    },
                    new SagaStep() {
                        public String name() {
                            return "ScheduleShipment";
                        }

                        public void execute(SagaContext ctx) throws SagaStepFailedException {
                            shipping.schedule(ctx.orderId);
                        }

                        public void compensate(SagaContext ctx) {
                            shipping.cancelShipment(ctx.orderId);
                        }
                    }
            );
        }

        void execute(SagaContext ctx, OrderService orderService) {
            // LIFO stack: compensations run in REVERSE order of execution.
            Deque<SagaStep> completed = new ArrayDeque<>();

            for (SagaStep step : steps) {
                System.out.println("  --> STEP: " + step.name());
                try {
                    step.execute(ctx);
                    completed.push(step); // only record AFTER success
                } catch (SagaStepFailedException e) {
                    System.out.println("  !!! STEP FAILED: " + step.name() + " - " + e.getMessage());
                    System.out.println("  === BEGINNING COMPENSATION (reverse order) ===");
                    compensate(completed, ctx);
                    ctx.finalStatus = "CANCELLED";
                    return;
                }
            }

            orderService.confirm(ctx.orderId);
            ctx.finalStatus = "CONFIRMED";
        }

        private void compensate(Deque<SagaStep> completed, SagaContext ctx) {
            while (!completed.isEmpty()) {
                SagaStep step = completed.pop(); // LIFO - reverse order
                System.out.println("  <-- COMPENSATING: " + step.name());
                try {
                    step.compensate(ctx);
                } catch (Exception e) {
                    // A FAILING COMPENSATION is the worst case in a saga -
                    // the system is now in an inconsistent state that code
                    // cannot fix. Real systems log loudly, alert on-call,
                    // and often park the saga in a "needs manual
                    // intervention" queue. Compensations should be designed
                    // to be as close to infallible as possible.
                    System.out.println("  XXX COMPENSATION FAILED for " + step.name()
                            + " - MANUAL INTERVENTION REQUIRED: " + e.getMessage());
                }
            }
        }
    }

    // ===================== Simulation =====================
    public static void main(String[] args) {

        System.out.println("################ SCENARIO 1: HAPPY PATH ################\n");
        run("ORDER-1", "LAPTOP-01", 2, 2400.0, 10, 5000.0);

        System.out.println("\n\n##### SCENARIO 2: PAYMENT FAILS (compensate 2 steps) #####\n");
        run("ORDER-2", "LAPTOP-01", 3, 7200.0, 10, 5000.0);

        System.out.println("\n\n#### SCENARIO 3: SHIPPING FAILS (compensate 3 steps) ####\n");
        run("ORDER-3-FAILSHIP", "LAPTOP-01", 2, 2400.0, 10, 5000.0);

        System.out.println("\n\n### SCENARIO 4: STOCK FAILS (compensate 1 step only) ###\n");
        run("ORDER-4", "LAPTOP-01", 50, 2400.0, 10, 5000.0);
    }

    static void run(String orderId, String productId, int qty, double amount,
                    int initialStock, double creditLimit) {
        OrderService orderService = new OrderService();
        InventoryService inventory = new InventoryService(Map.of(productId, initialStock));
        PaymentService payment = new PaymentService(creditLimit);
        ShippingService shipping = new ShippingService();

        OrderSagaOrchestrator orchestrator =
                new OrderSagaOrchestrator(orderService, inventory, payment, shipping);

        SagaContext ctx = new SagaContext(orderId, productId, qty, amount);
        orchestrator.execute(ctx, orderService);

        System.out.println("\n  RESULT: status=" + ctx.finalStatus
                + " | stock=" + inventory.get(productId) + " (started at " + initialStock + ")"
                + " | refunds=" + payment.refunds.size()
                + " | shipments=" + shipping.shipments.size());
    }
}
