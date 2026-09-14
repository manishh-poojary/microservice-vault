package com.toolkit.microservices.vault.communication.synchronous.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "inventory-service",
        url = "${inventory-service.url}",   // e.g. http://localhost:8081 - swap for service discovery in real deployments
        configuration = FeignConfig.class,
        fallback = InventoryFeignClientFallback.class)
public interface InventoryFeignClient {

    @GetMapping("/api/stock/{productId}")
    int checkStock(@PathVariable("productId") String productId);

    @PostMapping("/api/stock/{productId}/reserve")
    void reserveStock(@PathVariable("productId") String productId,
                      @RequestParam("quantity") int quantity,
                      @RequestHeader("X-Request-Id") String requestId);
}
