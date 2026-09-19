package com.toolkit.microservices.vault.datamanagement.cqrs;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Required for @Async to actually run listeners on a background thread
 * instead of silently running them synchronously (a very common gotcha -
 * @Async does NOTHING without this enabled somewhere in the app).
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}