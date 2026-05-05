package com.fraudstream.processor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafkaStreams;

@SpringBootApplication
@EnableKafkaStreams
public class FraudStreamApplication {
    public static void main(String[] args) {
        SpringApplication.run(FraudStreamApplication.class, args);
    }
}
