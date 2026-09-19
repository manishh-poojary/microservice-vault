package com.toolkit.microservices.vault.datamanagement.cqrs;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderWriteRepository extends JpaRepository<OrderEntity, String> {}

