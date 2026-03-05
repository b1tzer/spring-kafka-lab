package xpro.wang.kafkalab.server.service;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.stereotype.Service;
import xpro.wang.kafkalab.server.model.LabRealtimeEventType;
import xpro.wang.kafkalab.server.model.ProducerRegisterRequest;
import xpro.wang.kafkalab.server.model.ProducerSendRequest;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Producer service for sending test messages to Kafka.
 */
@Service
public class ProducerLabService {

    private static final int MAX_RECENT_MESSAGES_PER_PARTITION = 10;

    private final RuntimeKafkaConnectionService runtimeKafkaConnectionService;
    private final LabRealtimeWebSocketHandler labRealtimeWebSocketHandler;
    private final LabActivityLogService labActivityLogService;
    private final Map<String, ProducerRuntime> activeProducers = new ConcurrentHashMap<>();
    private final Map<String, ManagedProducer> managedProducers = new ConcurrentHashMap<>();
    private final Map<String, Deque<Map<String, Object>>> recentMessagesByPartition = new ConcurrentHashMap<>();

    public ProducerLabService(
            RuntimeKafkaConnectionService runtimeKafkaConnectionService,
            LabRealtimeWebSocketHandler labRealtimeWebSocketHandler,
            LabActivityLogService labActivityLogService
    ) {
        this.runtimeKafkaConnectionService = runtimeKafkaConnectionService;
        this.labRealtimeWebSocketHandler = labRealtimeWebSocketHandler;
        this.labActivityLogService = labActivityLogService;
    }

    /**
     * Sends messages based on request options.
     *
     * @param request send request
     * @return sent message count
     * @throws Exception when send fails
     */
    public int send(ProducerSendRequest request) throws Exception {
        String producerId = UUID.randomUUID().toString();
        return sendInternal(request, producerId, false).count();
    }

    public Map<String, Object> sendWithMetadata(ProducerSendRequest request) throws Exception {
        String producerId = UUID.randomUUID().toString();
        SendResult result = sendInternal(request, producerId, false);
        return Map.of(
                "count", result.count(),
                "topic", request.topic(),
                "messages", result.messages()
        );
    }

        public Map<String, Object> registerManagedProducer(ProducerRegisterRequest request) {
        String producerId = normalizeId(request.producerId(), "producer");
        List<String> topics = request.topics().stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(topic -> !topic.isBlank())
            .distinct()
            .toList();
        if (topics.isEmpty()) {
            throw new IllegalArgumentException("Producer topics cannot be empty");
        }

        ManagedProducer managed = new ManagedProducer(
            producerId,
            topics,
            Instant.now().toString(),
            null
        );
        managedProducers.put(producerId, managed);

        return Map.of(
            "producerId", managed.producerId(),
            "topics", managed.topics(),
            "createdAt", managed.createdAt(),
            "lastSentAt", managed.lastSentAt() == null ? "" : managed.lastSentAt()
        );
        }

        public List<Map<String, Object>> listManagedProducers() {
        return managedProducers.values().stream()
            .sorted((a, b) -> a.producerId().compareToIgnoreCase(b.producerId()))
            .map(item -> Map.of(
                "producerId", item.producerId(),
                "topics", item.topics(),
                "createdAt", item.createdAt(),
                "lastSentAt", item.lastSentAt() == null ? "" : item.lastSentAt()
            ))
            .toList();
        }

        public Map<String, Object> sendByManagedProducer(String producerId, ProducerSendRequest request) throws Exception {
        ManagedProducer managed = managedProducers.get(producerId);
        if (managed == null) {
            throw new IllegalArgumentException("Producer not found: " + producerId);
        }
        if (!managed.topics().contains(request.topic())) {
            throw new IllegalArgumentException("Topic " + request.topic() + " is not subscribed by producer " + producerId);
        }

            SendResult result = sendInternal(request, producerId, true);
        managedProducers.computeIfPresent(producerId, (id, old) -> new ManagedProducer(
            old.producerId(),
            old.topics(),
            old.createdAt(),
            Instant.now().toString()
        ));
            return Map.of(
                    "count", result.count(),
                    "topic", request.topic(),
                    "producerId", producerId,
                    "messages", result.messages()
            );
        }

    public Map<String, Object> updateManagedProducerTopics(String producerId, List<String> rawTopics) {
        ManagedProducer managed = managedProducers.get(producerId);
        if (managed == null) {
            throw new IllegalArgumentException("Producer not found: " + producerId);
        }

        List<String> topics = normalizeTopics(rawTopics);
        ManagedProducer updated = new ManagedProducer(
                managed.producerId(),
                topics,
                managed.createdAt(),
                managed.lastSentAt()
        );
        managedProducers.put(producerId, updated);
        return Map.of(
                "producerId", updated.producerId(),
                "topics", updated.topics(),
                "createdAt", updated.createdAt(),
                "lastSentAt", updated.lastSentAt() == null ? "" : updated.lastSentAt()
        );
    }

    public Map<String, Object> deleteManagedProducer(String producerId) {
        ManagedProducer removed = managedProducers.remove(producerId);
        if (removed == null) {
            throw new IllegalArgumentException("Producer not found: " + producerId);
        }
        return Map.of(
                "producerId", producerId,
                "status", "DELETED"
        );
    }

    private SendResult sendInternal(ProducerSendRequest request, String producerId, boolean managed) throws Exception {
        int count = request.count() == null ? 1 : request.count();
        long delay = request.delay() == null ? 0L : request.delay();
        List<Map<String, Object>> sentMessages = new ArrayList<>();

        activeProducers.put(producerId, new ProducerRuntime(
                producerId,
                request.topic(),
                count,
                Instant.now().toString(),
            managed ? "RUNNING_MANAGED" : "RUNNING"
        ));

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerConfigs(request.transactional()))) {
            if (request.transactional()) {
                producer.initTransactions();
                producer.beginTransaction();
                for (int i = 0; i < count; i++) {
                    sentMessages.add(sendOne(producer, request, i, producerId));
                    sleep(delay);
                }
                producer.commitTransaction();
                return new SendResult(count, sentMessages);
            }

            for (int i = 0; i < count; i++) {
                sentMessages.add(sendOne(producer, request, i, producerId));
                sleep(delay);
            }
            return new SendResult(count, sentMessages);
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

    private Map<String, Object> sendOne(KafkaProducer<String, String> producer, ProducerSendRequest request, int index, String producerId) throws Exception {
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

        Map<String, Object> sent = Map.of(
            "producerId", producerId,
            "topic", request.topic(),
            "partition", metadata.partition(),
            "offset", metadata.offset(),
            "timestamp", Instant.ofEpochMilli(metadata.timestamp()).toString(),
            "key", key,
            "value", payload
        );

        labRealtimeWebSocketHandler.publish(LabRealtimeEventType.PRODUCER_MESSAGE, sent);
        Map<String, Object> activity = labActivityLogService.append(
            "success",
            "PRODUCE",
            "Producer sent message",
            request.topic(),
            Map.of(
                "producerId", producerId,
                "topic", request.topic(),
                "partition", metadata.partition(),
                "offset", metadata.offset(),
                "key", key,
                "value", payload
            )
        );
        labRealtimeWebSocketHandler.publish(LabRealtimeEventType.ACTIVITY_LOG, activity);

        return sent;
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

    private String normalizeId(String raw, String prefix) {
        if (raw != null && !raw.isBlank()) {
            return raw.trim();
        }
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private List<String> normalizeTopics(List<String> rawTopics) {
        if (rawTopics == null) {
            throw new IllegalArgumentException("Producer topics cannot be empty");
        }
        List<String> topics = rawTopics.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(topic -> !topic.isBlank())
                .distinct()
                .toList();
        if (topics.isEmpty()) {
            throw new IllegalArgumentException("Producer topics cannot be empty");
        }
        return topics;
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

        private record ManagedProducer(
            String producerId,
            List<String> topics,
            String createdAt,
            String lastSentAt
        ) {
        }

    private record SendResult(
            int count,
            List<Map<String, Object>> messages
    ) {
    }
}
