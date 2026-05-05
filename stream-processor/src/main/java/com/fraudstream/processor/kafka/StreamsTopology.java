package com.fraudstream.processor.kafka;

import com.fraudstream.processor.model.FraudFlag;
import com.fraudstream.processor.model.Transaction;
import com.fraudstream.processor.rules.RuleEngine;
import com.fraudstream.processor.service.FlagPersistenceService;
import com.fraudstream.processor.websocket.FlagWebSocketHandler;
import jakarta.annotation.PostConstruct;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.support.serializer.JsonSerde;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class StreamsTopology {

    private static final Logger log = LoggerFactory.getLogger(StreamsTopology.class);

    @Autowired private StreamsBuilder streamsBuilder;
    @Autowired private RuleEngine ruleEngine;
    @Autowired private FlagPersistenceService persistenceService;
    @Autowired private FlagWebSocketHandler wsHandler;

    @Value("${fraud.topic.transactions:transactions}") private String txTopic;
    @Value("${fraud.topic.flags:flags}") private String flagTopic;

    @PostConstruct
    public void buildTopology() {
        JsonSerde<Transaction> txSerde = new JsonSerde<>(Transaction.class);
        txSerde.configure(Map.of(
            "spring.json.trusted.packages", "com.fraudstream.processor.model"
        ), false);

        JsonSerde<FraudFlag> flagSerde = new JsonSerde<>(FraudFlag.class);
        flagSerde.configure(Map.of(
            "spring.json.trusted.packages", "com.fraudstream.processor.model"
        ), false);

        KStream<String, Transaction> txStream = streamsBuilder.stream(
            txTopic, Consumed.with(Serdes.String(), txSerde));

        KStream<String, FraudFlag> flagStream = txStream.flatMap((key, tx) -> {
            if (tx == null) return List.of();
            long start = System.currentTimeMillis();
            List<FraudFlag> flags = ruleEngine.evaluate(tx);
            long elapsed = System.currentTimeMillis() - start;
            String corrId = UUID.randomUUID().toString();
            flags.forEach(f -> {
                try {
                    persistenceService.persist(f, corrId, flags.size(), elapsed);
                } catch (Exception e) {
                    log.error("Failed to persist flag for tx {}", tx.txId(), e);
                }
                wsHandler.broadcast(f);
            });
            return flags.stream()
                .map(f -> new KeyValue<>(f.accountId(), f))
                .collect(Collectors.toList());
        });

        flagStream.to(flagTopic, Produced.with(Serdes.String(), flagSerde));
        log.info("Kafka Streams topology built: {} -> [rules] -> {}", txTopic, flagTopic);
    }
}
