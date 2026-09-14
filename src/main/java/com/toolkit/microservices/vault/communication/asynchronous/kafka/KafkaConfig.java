package com.toolkit.microservices.vault.communication.asynchronous.kafka;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.FixedBackOff;

/**
 * KafkaConfig
 * -------------
 * Configures the DEAD LETTER QUEUE (DLQ) pattern: messages that
 * repeatedly fail processing are, after a bounded number of retries,
 * published to a separate "*.DLQ" topic instead of being retried
 * forever or silently dropped. This lets you:
 * - Keep the main topic's consumer moving (a single poison-pill
 * message doesn't block processing of everything after it)
 * - Inspect/replay failed messages later without losing them
 * - Alert on DLQ volume as a health signal
 */
@Configuration
public class KafkaConfig {

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {

        // Publishes failed records to "<original-topic>.DLQ" automatically
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".DLQ", record.partition()));

        // Retry 3 times with a 1-second pause between attempts, THEN send to DLQ
        FixedBackOff backOff = new FixedBackOff(1000L, 3);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

        // Some exceptions are NEVER worth retrying (e.g. deserialization
        // failures - the message is malformed, retrying won't fix that) -
        // send straight to DLQ without wasting the retry budget.
        errorHandler.addNotRetryableExceptions(DeserializationException.class);

        return errorHandler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object>
    kafkaListenerContainerFactory(ConsumerFactory<String, Object> consumerFactory,
                                  DefaultErrorHandler errorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);

        // Number of consumer threads WITHIN this single instance - combines
        // with the competing-consumers behavior across instances/group-id.
        // Total parallelism is capped by the topic's partition count either way.
        factory.setConcurrency(3);

        return factory;
    }
}
