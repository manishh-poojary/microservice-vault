package com.toolkit.microservices.vault;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

import java.util.concurrent.CompletableFuture;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.toolkit.microservices.vault.communication.synchronous.feign")
public class MicroservicesVaultApplication {

    public static void main(String[] args) {
        CompletableFuture<Integer> completableFuture = new CompletableFuture<>();


        SpringApplication.run(MicroservicesVaultApplication.class, args);
    }

}
