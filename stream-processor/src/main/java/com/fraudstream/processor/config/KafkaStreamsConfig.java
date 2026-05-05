package com.fraudstream.processor.config;

import org.springframework.context.annotation.Configuration;

/**
 * Kafka Streams is auto-configured by Spring Boot when spring.kafka.streams.application-id
 * is set in application.yml and @EnableKafkaStreams is on the main app class.
 * Additional Kafka Streams tuning can be placed here if needed.
 */
@Configuration
public class KafkaStreamsConfig {
    // Intentionally minimal — Spring Boot auto-configures KafkaStreams
    // from spring.kafka.streams.* properties in application.yml.
}
