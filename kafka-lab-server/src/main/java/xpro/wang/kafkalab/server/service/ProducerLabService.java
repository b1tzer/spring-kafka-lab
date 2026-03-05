package xpro.wang.kafkalab.server.service;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.stereotype.Service;
import xpro.wang.kafkalab.server.model.ProducerSendRequest;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Producer service for sending test messages to Kafka.
 */
@Service
public class ProducerLabService {

    private static final int MAX_RECENT_MESSAGES_PER_PARTITION = 10;

    private final RuntimeKafkaConnectionService runtimeKafkaConnectionService;
    private final Map<String, ProducerRuntime> activeProducers = new ConcurrentHashMap<>();
    private final Map<String, Deque<Map<String, Object>>> recentMessagesByPartition = new ConcurrentHashMap<>();

    public ProducerLabService(RuntimeKafkaConnectionService runtimeKafkaConnectionService) {
        this.runtimeKafkaConnectionService = runtimeKafkaConnectionService;
    }

    /**
     * Sends messages based on request options.
     *
     * @param request send request
     * @return sent message count
     * @throws Exception when send fails
     */
    public int send(ProducerSendRequest request) throws Exception {
        int count = request.count() == null ? 1 : request.count();
        long delay = request.delay() == null ? 0L : request.delay();
        String producerId = UUID.randomUUID().toString();

        activeProducers.put(producerId, new ProducerRuntime(
                producerId,
                request.topic(),
                count,
                Instant.now().toString(),
                "RUNNING"
        ));

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerConfigs(request.transactional()))) {
            if (request.transactional()) {
                producer.initTransactions();
                producer.beginTransaction();
                for (int i = 0; i < count; i++) {
                    sendOne(producer, request, i, producerId);
                    sleep(delay);
                }
                producer.commitTransaction();
                return count;
            }

            for (int i = 0; i < count; i++) {
                sendOne(producer, request, i, producerId);
                sleep(delay);
            }
            return count;
        } finally {
            activeProducers.remove(producerId);
        }
    }

    private Map<String, Object> producerConfigs(boolean transactional) {
        Map<String, Object> configs = new java.util.HashMap<>();
        configs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, runtimeKafkaConnectionService.bootstrapServers());
        configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        configs.put(ProducerConfig.ACKS_CONFIG, "all");
        configs.put(ProducerConfig.RETRIES_CONFIG, 3);
        if (transactional) {
            configs.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
            configs.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "kafka-lab-tx-" + UUID.randomUUID());
        }
        return configs;
    }

    private void sendOne(KafkaProducer<String, String> producer, ProducerSendRequest request, int index, String producerId) throws Exception {
        String key = request.key();
        if (key == null || key.isBlank()) {
            key = UUID.randomUUID().toString();
        }
        String payload = request.count() != null && request.count() > 1
                ? request.message() + " #" + index
                : request.message();

        ProducerRecord<String, String> record = request.partition() == null
                ? new ProducerRecord<>(request.topic(), key, payload)
                : new ProducerRecord<>(request.topic(), request.partition(), key, payload);

        RecordMetadata metadata = producer.send(record).get();
        appendRecentMessage(request.topic(), metadata.partition(), Map.of(
                "producerId", producerId,
                "topic", request.topic(),
                "partition", metadata.partition(),
                "offset", metadata.offset(),
                "timestamp", Instant.ofEpochMilli(metadata.timestamp()).toString(),
                "key", key,
                "value", payload
        ));
    }

    public List<Map<String, Object>> activeProducers() {
        return activeProducers.values().stream()
                .map(runtime -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("producerId", runtime.producerId());
                    item.put("topic", runtime.topic());
                    item.put("targetCount", runtime.targetCount());
                    item.put("startedAt", runtime.startedAt());
                    item.put("status", runtime.status());
                    return item;
                })
                .toList();
    }

    public List<Map<String, Object>> recentMessages(String topic, int partition, int limit) {
        String key = partitionKey(topic, partition);
        Deque<Map<String, Object>> deque = recentMessagesByPartition.get(key);
        if (deque == null || deque.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> all = new ArrayList<>(deque);
        int size = all.size();
        int from = Math.max(0, size - Math.max(1, limit));
        return all.subList(from, size);
    }

    private void appendRecentMessage(String topic, int partition, Map<String, Object> message) {
        String key = partitionKey(topic, partition);
        recentMessagesByPartition.compute(key, (k, oldDeque) -> {
            Deque<Map<String, Object>> deque = oldDeque == null ? new ArrayDeque<>() : oldDeque;
            deque.addLast(new HashMap<>(message));
            while (deque.size() > MAX_RECENT_MESSAGES_PER_PARTITION) {
                deque.pollFirst();
            }
            return deque;
        });
    }

    private String partitionKey(String topic, int partition) {
        return topic + "::" + partition;
    }

    private void sleep(long delay) {
        if (delay <= 0) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while delayed sending", ex);
        }
    }

    private record ProducerRuntime(
            String producerId,
            String topic,
            int targetCount,
            String startedAt,
            String status
    ) {
    }
}
