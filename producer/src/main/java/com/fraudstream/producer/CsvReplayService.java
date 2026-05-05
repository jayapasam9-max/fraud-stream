package com.fraudstream.producer;

import com.fraudstream.producer.model.Transaction;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class CsvReplayService {

    private static final Logger log = LoggerFactory.getLogger(CsvReplayService.class);

    private final KafkaTemplate<String, Transaction> kafkaTemplate;

    @Value("${producer.rate:100}") private int rate;
    @Value("${producer.topic:transactions}") private String topic;
    @Value("${producer.csv-path:../sample-data/transactions.csv}") private String csvPath;

    private List<Transaction> transactions = new ArrayList<>();
    private int position = 0;
    private long totalSent = 0;

    public CsvReplayService(KafkaTemplate<String, Transaction> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @PostConstruct
    public void loadCsv() {
        log.info("Loading transactions from {}", csvPath);
        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            br.readLine(); // skip header
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",", -1);
                if (parts.length < 11) continue;
                try {
                    Transaction tx = new Transaction(
                        parts[0].trim(),
                        parts[1].trim(),
                        parts[2].trim(),
                        new BigDecimal(parts[3].trim()),
                        parts[4].trim(),
                        parts[5].trim(),
                        parts[6].trim(),
                        Double.parseDouble(parts[7].trim()),
                        Double.parseDouble(parts[8].trim()),
                        Instant.parse(parts[9].trim()),
                        "1".equals(parts[10].trim())
                    );
                    transactions.add(tx);
                } catch (Exception e) {
                    log.warn("Skipping malformed row: {}", line);
                }
            }
        } catch (Exception e) {
            log.error("Failed to load CSV from {}: {}", csvPath, e.getMessage());
        }
        log.info("Loaded {} transactions from CSV", transactions.size());
    }

    @Scheduled(fixedDelay = 1000)
    public void sendBatch() {
        if (transactions.isEmpty()) {
            log.warn("No transactions loaded — check csv-path config");
            return;
        }
        for (int i = 0; i < rate; i++) {
            Transaction tx = transactions.get(position % transactions.size());
            kafkaTemplate.send(topic, tx.txId(), tx);
            position++;
            totalSent++;
        }
        if (totalSent % 5000 == 0) {
            log.info("Sent {} transactions to Kafka topic '{}'", totalSent, topic);
        }
    }
}
