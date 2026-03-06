package xpro.wang.kafkalab.server.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.stereotype.Service;
import xpro.wang.kafkalab.server.model.LabRealtimeEventType;
import xpro.wang.kafkalab.server.model.ProducerAutoSendRequest;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Producer service for sending test messages to Kafka.
 */
@Service
public class ProducerLabService {

    private static final Logger log = LoggerFactory.getLogger(ProducerLabService.class);

    private static final int MAX_RECENT_MESSAGES_PER_PARTITION = 10;
    private static final String AUTO_SEND_DEFAULT_MESSAGE = "auto kafka lab message";

    private final RuntimeKafkaConnectionService runtimeKafkaConnectionService;
    private final LabRealtimeWebSocketHandler labRealtimeWebSocketHandler;
    private final LabActivityLogService labActivityLogService;
    private final Map<String, ProducerRuntime> activeProducers = new ConcurrentHashMap<>();
    private final Map<String, ManagedProducer> managedProducers = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> autoSendTasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService autoSendExecutor = Executors.newScheduledThreadPool(4);
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
        List<String> topics = normalizeTopics(request.topics());

        ManagedProducer managed = new ManagedProducer(
                producerId,
                topics,
                Instant.now().toString(),
                null,
                new AutoSendTaskStatus(false, "", 0d, 0L, "", "", "")
        );
        managedProducers.put(producerId, managed);
        log.info("Managed producer registered: producerId={}, topics={}", producerId, topics);

        return toManagedProducerMap(managed);
    }

    public List<Map<String, Object>> listManagedProducers() {
        return managedProducers.values().stream()
                .sorted((a, b) -> a.producerId().compareToIgnoreCase(b.producerId()))
                .map(this::toManagedProducerMap)
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
                Instant.now().toString(),
                old.autoTask()
        ));

        log.info("Managed producer sent messages: producerId={}, topic={}, count={}", producerId, request.topic(), result.count());
        return Map.of(
                "count", result.count(),
                "topic", request.topic(),
                "producerId", producerId,
                "messages", result.messages()
        );
    }

    public Map<String, Object> startAutoSendTask(String producerId, ProducerAutoSendRequest request) {
        ManagedProducer managed = managedProducers.get(producerId);
        if (managed == null) {
            throw new IllegalArgumentException("Producer not found: " + producerId);
        }

        String topic = request.topic() == null ? "" : request.topic().trim();
        if (topic.isBlank()) {
            throw new IllegalArgumentException("Auto send topic cannot be empty");
        }
        if (!managed.topics().contains(topic)) {
            throw new IllegalArgumentException("Topic " + topic + " is not subscribed by producer " + producerId);
        }

        double frequencyPerSecond = request.frequencyPerSecond() == null ? 1.0d : request.frequencyPerSecond();
        if (frequencyPerSecond <= 0d) {
            throw new IllegalArgumentException("frequencyPerSecond must be > 0");
        }

        long intervalMs = Math.max(100L, Math.round(1000d / frequencyPerSecond));
        stopAutoSendTaskInternal(producerId, false);
        log.info("Starting producer auto-send task: producerId={}, topic={}, frequencyPerSecond={}, intervalMs={}",
            producerId, topic, frequencyPerSecond, intervalMs);

        String startedAt = Instant.now().toString();
        managedProducers.computeIfPresent(producerId, (id, old) -> new ManagedProducer(
                old.producerId(),
                old.topics(),
                old.createdAt(),
                old.lastSentAt(),
                new AutoSendTaskStatus(true, topic, frequencyPerSecond, intervalMs, startedAt,
                        old.autoTask().lastSentAt(), "")
        ));

        ScheduledFuture<?> future = autoSendExecutor.scheduleAtFixedRate(() -> {
            try {
                sendByManagedProducer(producerId, new ProducerSendRequest(
                        topic,
                        "",
                        AUTO_SEND_DEFAULT_MESSAGE,
                        null,
                        0L,
                        1,
                        false
                ));
                updateAutoTaskHeartbeat(producerId, Instant.now().toString(), "");
            } catch (Exception ex) {
                log.error("Producer auto-send tick failed: producerId={}, topic={}", producerId, topic, ex);
                updateAutoTaskHeartbeat(producerId, "", ex.getMessage() == null ? "Auto send failed" : ex.getMessage());
            }
        }, 0L, intervalMs, TimeUnit.MILLISECONDS);

        autoSendTasks.put(producerId, future);
        return toManagedProducerMap(requireManagedProducer(producerId));
    }

    public Map<String, Object> stopAutoSendTask(String producerId) {
        ManagedProducer managed = requireManagedProducer(producerId);
        log.info("Stopping producer auto-send task: producerId={}", producerId);
        stopAutoSendTaskInternal(producerId, true);
        return toManagedProducerMap(managedProducers.getOrDefault(producerId, managed));
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
                managed.lastSentAt(),
                managed.autoTask()
        );
        managedProducers.put(producerId, updated);
        log.info("Managed producer topics updated: producerId={}, topics={}", producerId, topics);
        return toManagedProducerMap(updated);
    }

    public Map<String, Object> deleteManagedProducer(String producerId) {
        stopAutoSendTaskInternal(producerId, true);
        ManagedProducer removed = managedProducers.remove(producerId);
        if (removed == null) {
            throw new IllegalArgumentException("Producer not found: " + producerId);
        }
        log.info("Managed producer deleted: producerId={}", producerId);
        return Map.of(
                "producerId", producerId,
                "status", "DELETED"
        );
    }

    @PreDestroy
    public void destroy() {
        log.info("Destroying producer service scheduler. activeAutoTasks={}", autoSendTasks.size());
        autoSendTasks.values().forEach(task -> task.cancel(true));
        autoSendTasks.clear();
        autoSendExecutor.shutdownNow();
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
        log.info("Producer send started: producerId={}, topic={}, count={}, delay={}, transactional={}, managed={}",
                producerId, request.topic(), count, delay, request.transactional(), managed);

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
            log.info("Producer send finished: producerId={}, topic={}", producerId, request.topic());
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

    private ManagedProducer requireManagedProducer(String producerId) {
        ManagedProducer managed = managedProducers.get(producerId);
        if (managed == null) {
            throw new IllegalArgumentException("Producer not found: " + producerId);
        }
        return managed;
    }

    private void stopAutoSendTaskInternal(String producerId, boolean markStopped) {
        ScheduledFuture<?> task = autoSendTasks.remove(producerId);
        if (task != null) {
            task.cancel(true);
        }
        if (!markStopped) {
            return;
        }

        managedProducers.computeIfPresent(producerId, (id, old) -> {
            AutoSendTaskStatus prev = old.autoTask();
            return new ManagedProducer(
                    old.producerId(),
                    old.topics(),
                    old.createdAt(),
                    old.lastSentAt(),
                    new AutoSendTaskStatus(false,
                            prev.topic(),
                            prev.frequencyPerSecond(),
                            prev.intervalMs(),
                            prev.startedAt(),
                            prev.lastSentAt(),
                            prev.lastError())
            );
        });
    }

    private void updateAutoTaskHeartbeat(String producerId, String lastSentAt, String lastError) {
        managedProducers.computeIfPresent(producerId, (id, old) -> {
            AutoSendTaskStatus prev = old.autoTask();
            return new ManagedProducer(
                    old.producerId(),
                    old.topics(),
                    old.createdAt(),
                    lastSentAt == null || lastSentAt.isBlank() ? old.lastSentAt() : lastSentAt,
                    new AutoSendTaskStatus(
                            prev.running(),
                            prev.topic(),
                            prev.frequencyPerSecond(),
                            prev.intervalMs(),
                            prev.startedAt(),
                            lastSentAt == null || lastSentAt.isBlank() ? prev.lastSentAt() : lastSentAt,
                            lastError == null ? "" : lastError
                    )
            );
        });
    }

    private Map<String, Object> toManagedProducerMap(ManagedProducer item) {
        Map<String, Object> autoTask = new HashMap<>();
        autoTask.put("running", item.autoTask().running());
        autoTask.put("topic", item.autoTask().topic());
        autoTask.put("frequencyPerSecond", item.autoTask().frequencyPerSecond());
        autoTask.put("intervalMs", item.autoTask().intervalMs());
        autoTask.put("startedAt", item.autoTask().startedAt());
        autoTask.put("lastSentAt", item.autoTask().lastSentAt());
        autoTask.put("lastError", item.autoTask().lastError());

        Map<String, Object> data = new HashMap<>();
        data.put("producerId", item.producerId());
        data.put("topics", item.topics());
        data.put("createdAt", item.createdAt());
        data.put("lastSentAt", item.lastSentAt() == null ? "" : item.lastSentAt());
        data.put("autoTask", autoTask);
        return data;
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
            String lastSentAt,
            AutoSendTaskStatus autoTask
    ) {
    }

    private record AutoSendTaskStatus(
            boolean running,
            String topic,
            double frequencyPerSecond,
            long intervalMs,
            String startedAt,
            String lastSentAt,
            String lastError
    ) {
    }

    private record SendResult(
            int count,
            List<Map<String, Object>> messages
    ) {
    }
}
