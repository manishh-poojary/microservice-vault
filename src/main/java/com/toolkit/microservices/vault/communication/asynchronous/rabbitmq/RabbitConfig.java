package com.toolkit.microservices.vault.communication.asynchronous.rabbitmq;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitConfig
 * --------------
 * NOTE ON THE JSON CONVERTER (updated):
 * <p>
 * Spring AMQP 4.0 (paired with Spring Boot 4.x, which moved to Jackson
 * 3.x) deprecated Jackson2JsonMessageConverter for removal, in favor of
 * JacksonJsonMessageConverter (shown below). This is a real breaking
 * change, not a cosmetic rename - Jackson 3 lives under a DIFFERENT
 * package namespace (tools.jackson.databind.*) than Jackson 2
 * (com.fasterxml.jackson.databind.*), so this isn't a drop-in swap if
 * you have custom ObjectMapper/JsonMapper configuration elsewhere.
 * <p>
 * WHICH ONE YOU ACTUALLY NEED DEPENDS ON YOUR SPRING BOOT VERSION:
 * <p>
 * - Spring Boot 4.x (Spring AMQP 4.x, Jackson 3.x)
 * -> use JacksonJsonMessageConverter (shown below) - this is what
 * this file now uses.
 * <p>
 * - Spring Boot 3.x (Spring AMQP 3.x, Jackson 2.x) - still very common,
 * probably what most existing projects are on right now
 * -> Jackson2JsonMessageConverter is NOT deprecated on this version
 * line at all - it's the correct, current choice. Don't "fix"
 * a non-problem by trying to use JacksonJsonMessageConverter on
 * Spring Boot 3.x - that class doesn't exist there yet.
 * <p>
 * Check your parent POM / BOM version (spring-boot-starter-parent or
 * spring-boot-dependencies) to know which line you're on. If unsure,
 * run: mvn dependency:tree | grep spring-amqp
 */
@Configuration
public class RabbitConfig {

    static final String EXCHANGE = "order.events.exchange";
    static final String ORDER_CREATED_QUEUE = "inventory.order-created.queue";
    static final String ORDER_CREATED_ROUTING_KEY = "order.created";

    static final String DLX = "order.events.dlx";
    static final String DLQ = "inventory.order-created.dlq";

    @Bean
    public TopicExchange orderEventsExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with(DLQ);
    }

    @Bean
    public Queue orderCreatedQueue() {
        return QueueBuilder.durable(ORDER_CREATED_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", DLQ)
                .build();
    }

    @Bean
    public Binding orderCreatedBinding() {
        return BindingBuilder.bind(orderCreatedQueue())
                .to(orderEventsExchange())
                .with(ORDER_CREATED_ROUTING_KEY);
    }

    /**
     * --- JSON message conversion: Jackson 3 version (Spring Boot 4.x / Spring AMQP 4.x) ---
     * If you're on Spring Boot 3.x, see RabbitConfigSpringBoot3.java instead.
     * <p>
     * trustedPackages works the same conceptually as Kafka's
     * spring.json.trusted.packages from the Kafka example - refuses
     * to deserialize into arbitrary classes by default as a security
     * guard against deserialization gadget attacks.
     */
    @Bean
    public JacksonJsonMessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter("com.example.events");
    }

    /**
     * Fires if a message can't be routed to ANY queue at all (e.g. a
     * typo'd routing key with no matching binding) - requires
     * spring.rabbitmq.template.mandatory=true, set in application.yml.
     * Without this, unroutable messages are silently DROPPED by
     * default - a common and nasty gotcha in RabbitMQ setups.
     */

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         JacksonJsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        template.setReturnsCallback(returned ->
                System.out.println("Message could not be routed! Exchange=" + returned.getExchange()
                        + ", routingKey=" + returned.getRoutingKey() + ", replyCode=" + returned.getReplyCode()));
        return template;
    }
}