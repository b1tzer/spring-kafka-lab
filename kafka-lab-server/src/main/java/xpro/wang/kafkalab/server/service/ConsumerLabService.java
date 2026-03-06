package xpro.wang.kafkalab.server.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.stereotype.Service;
import xpro.wang.kafkalab.server.model.LabRealtimeEventType;
import xpro.wang.kafkalab.server.model.ConsumerRegisterRequest;
import xpro.wang.kafkalab.server.model.ConsumerRuntimeStatus;
import xpro.wang.kafkalab.server.model.ConsumerStartRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consumer simulation and managed consumer runtime service.
 */
@Service
public class ConsumerLabService {

    private static final Logger log = LoggerFactory.getLogger(ConsumerLabService.class);

    private final RuntimeKafkaConnectionService runtimeKafkaConnectionService;
    private final LabRealtimeWebSocketHandler labRealtimeWebSocketHandler;
    private final LabActivityLogService labActivityLogService;
    private final Map<String, ManagedConsumer> managedConsumers = new ConcurrentHashMap<>();
    private final Map<String, ConsumerWorker> workers = new ConcurrentHashMap<>();

    public ConsumerLabService(
            RuntimeKafkaConnectionService runtimeKafkaConnectionService,
            LabRealtimeWebSocketHandler labRealtimeWebSocketHandler,
            LabActivityLogService labActivityLogService) {
        this.runtimeKafkaConnectionService = runtimeKafkaConnectionService;
        this.labRealtimeWebSocketHandler = labRealtimeWebSocketHandler;
        this.labActivityLogService = labActivityLogService;
    }

    public Map<String, Object> registerConsumer(ConsumerRegisterRequest request) {
        String groupId = normalizeId(request.groupId(), "group");
        String clientId = normalizeId(request.clientId(), "consumer");

        List<String> topics = normalizeTopics(request.topics());

        ManagedConsumer managedConsumer = new ManagedConsumer(
                clientId,
                groupId,
                request.hostIp() == null || request.hostIp().isBlank() ? "127.0.0.1" : request.hostIp(),
                topics,
                request.autoCommit() == null || request.autoCommit(),
                ConsumerRuntimeStatus.STOPPED.name(),
                Instant.now().toString(),
                null
        );
        managedConsumers.put(clientId, managedConsumer);
        log.info("Managed consumer registered: clientId={}, groupId={}, topics={}, autoCommit={}",
            clientId, groupId, topics, managedConsumer.autoCommit());
        return toMap(managedConsumer);
    }

    public List<Map<String, Object>> listManagedConsumers() {
        return managedConsumers.values().stream()
                .sorted((a, b) -> a.clientId().compareToIgnoreCase(b.clientId()))
                .map(this::toMap)
                .toList();
    }

    /**
     * Backward compatible API used by existing controller/scenario.
     */
    public Map<String, Object> startConsumer(ConsumerStartRequest request) {
        String clientId = normalizeId(request.groupId(), "consumer");
        registerConsumer(new ConsumerRegisterRequest(
                request.groupId(),
                clientId,
                "127.0.0.1",
                List.of(request.topic()),
                request.autoCommit()
        ));
        return startByClientId(clientId);
    }

    public Map<String, Object> stopConsumer(String groupId) {
        List<String> matched = managedConsumers.values().stream()
                .filter(consumer -> consumer.groupId().equals(groupId))
                .map(ManagedConsumer::clientId)
                .toList();
        if (matched.isEmpty()) {
            return Map.of("groupId", groupId, "status", ConsumerRuntimeStatus.NOT_FOUND.name());
        }
        matched.forEach(this::stopByClientId);
        return Map.of("groupId", groupId, "status", ConsumerRuntimeStatus.STOPPED.name());
    }

    public Map<String, Object> startByClientId(String clientId) {
        ManagedConsumer managed = managedConsumers.get(clientId);
        if (managed == null) {
            throw new IllegalArgumentException("Consumer not found: " + clientId);
        }
        ConsumerWorker running = workers.get(clientId);
        if (running != null && running.running()) {
            return toMap(managed);
        }

        ConsumerWorker worker = new ConsumerWorker(managed);
        workers.put(clientId, worker);
        worker.start();
        log.info("Managed consumer started: clientId={}, groupId={}, topics={}",
            managed.clientId(), managed.groupId(), managed.topics());

        ManagedConsumer updated = new ManagedConsumer(
                managed.clientId(),
                managed.groupId(),
                managed.hostIp(),
                managed.topics(),
                managed.autoCommit(),
                ConsumerRuntimeStatus.RUNNING.name(),
                managed.createdAt(),
                Instant.now().toString()
        );
        managedConsumers.put(clientId, updated);
        return toMap(updated);
    }

    public Map<String, Object> stopByClientId(String clientId) {
        ManagedConsumer managed = managedConsumers.get(clientId);
        if (managed == null) {
            return Map.of("clientId", clientId, "status", ConsumerRuntimeStatus.NOT_FOUND.name());
        }

        ConsumerWorker worker = workers.remove(clientId);
        if (worker != null) {
            worker.stop();
        }
        log.info("Managed consumer stopped: clientId={}, groupId={}", managed.clientId(), managed.groupId());

        ManagedConsumer updated = new ManagedConsumer(
                managed.clientId(),
                managed.groupId(),
                managed.hostIp(),
                managed.topics(),
                managed.autoCommit(),
                ConsumerRuntimeStatus.STOPPED.name(),
                managed.createdAt(),
                managed.startedAt()
        );
        managedConsumers.put(clientId, updated);

        return toMap(updated);
    }

    public Map<String, Object> updateConsumerTopics(String clientId, List<String> rawTopics) {
        ManagedConsumer managed = managedConsumers.get(clientId);
        if (managed == null) {
            throw new IllegalArgumentException("Consumer not found: " + clientId);
        }

        List<String> topics = normalizeTopics(rawTopics);
        boolean wasRunning = ConsumerRuntimeStatus.RUNNING.name().equals(managed.status());
        if (wasRunning) {
            stopByClientId(clientId);
        }

        ManagedConsumer updated = new ManagedConsumer(
                managed.clientId(),
                managed.groupId(),
                managed.hostIp(),
                topics,
                managed.autoCommit(),
                wasRunning ? ConsumerRuntimeStatus.RUNNING.name() : ConsumerRuntimeStatus.STOPPED.name(),
                managed.createdAt(),
                wasRunning ? Instant.now().toString() : managed.startedAt()
        );
        managedConsumers.put(clientId, updated);

        if (wasRunning) {
            ConsumerWorker worker = new ConsumerWorker(updated);
            workers.put(clientId, worker);
            worker.start();
        }

        log.info("Managed consumer topics updated: clientId={}, topics={}, restarted={}", clientId, topics, wasRunning);

        return toMap(updated);
    }

    public Map<String, Object> deleteByClientId(String clientId) {
        ConsumerWorker worker = workers.remove(clientId);
        if (worker != null) {
            worker.stop();
        }

        ManagedConsumer removed = managedConsumers.remove(clientId);
        if (removed == null) {
            throw new IllegalArgumentException("Consumer not found: " + clientId);
        }

        log.info("Managed consumer deleted: clientId={}, groupId={}", removed.clientId(), removed.groupId());

        return Map.of(
                "clientId", clientId,
                "groupId", removed.groupId(),
                "status", "DELETED"
        );
    }

    public Map<String, Object> listGroups() throws Exception {
        log.info("Listing consumer groups from Kafka");
        try (AdminClient adminClient = adminClient()) {
            Collection<String> groups = adminClient.listConsumerGroups().all().get().stream()
                    .map(group -> group.groupId())
                    .toList();

            Map<String, ConsumerGroupDescription> descriptions = groups.isEmpty()
                    ? Map.of()
                    : adminClient.describeConsumerGroups(groups).all().get();

            List<Map<String, Object>> items = groups.stream().map(group -> {
                ConsumerGroupDescription description = descriptions.get(group);
                Map<String, Object> item = new HashMap<>();
                item.put("groupId", group);
                item.put("state", description == null ? "UNKNOWN" : String.valueOf(description.state()));
                item.put("members", description == null ? 0 : description.members().size());
                item.put("managedByLab", managedConsumers.values().stream().anyMatch(c -> c.groupId().equals(group)));
                return item;
            }).toList();

            return Map.of(
                    "groups", items,
                    "localRegistry", managedConsumers.values().stream().map(this::toMap).toList()
            );
        }
    }

    public List<Map<String, Object>> localConsumers() {
        return managedConsumers.values().stream()
                .filter(item -> ConsumerRuntimeStatus.RUNNING.name().equals(item.status()))
                .map(item -> {
                    Map<String, Object> copy = new HashMap<>();
                    copy.put("groupId", item.groupId());
                    copy.put("clientId", item.clientId());
                    copy.put("host", item.hostIp());
                    copy.put("topics", item.topics());
                    copy.put("topic", item.topics().isEmpty() ? "" : item.topics().get(0));
                    copy.put("status", item.status());
                    copy.put("startedAt", item.startedAt());
                    return copy;
                })
                .toList();
    }

    @PreDestroy
    public void destroy() {
        log.info("Destroying consumer workers: count={}", workers.size());
        workers.values().forEach(ConsumerWorker::stop);
        workers.clear();
    }

    private Map<String, Object> toMap(ManagedConsumer item) {
        Map<String, Object> data = new HashMap<>();
        data.put("clientId", item.clientId());
        data.put("groupId", item.groupId());
        data.put("hostIp", item.hostIp());
        data.put("topics", item.topics());
        data.put("autoCommit", item.autoCommit());
        data.put("status", item.status());
        data.put("createdAt", item.createdAt());
        data.put("startedAt", item.startedAt() == null ? "" : item.startedAt());
        return data;
    }

    private KafkaConsumer<String, String> consumerOf(ManagedConsumer managed) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, runtimeKafkaConnectionService.bootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, managed.groupId());
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, managed.clientId());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, managed.autoCommit());
        return new KafkaConsumer<>(props);
    }

    private AdminClient adminClient() {
        return AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                runtimeKafkaConnectionService.bootstrapServers()
        ));
    }

    private String normalizeId(String raw, String prefix) {
        if (raw != null && !raw.isBlank()) {
            return raw.trim();
        }
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private List<String> normalizeTopics(List<String> rawTopics) {
        if (rawTopics == null) {
            throw new IllegalArgumentException("Consumer topics cannot be empty");
        }
        List<String> topics = rawTopics.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(topic -> !topic.isBlank())
                .distinct()
                .toList();
        if (topics.isEmpty()) {
            throw new IllegalArgumentException("Consumer topics cannot be empty");
        }
        return topics;
    }

    private record ManagedConsumer(
            String clientId,
            String groupId,
            String hostIp,
            List<String> topics,
            boolean autoCommit,
            String status,
            String createdAt,
            String startedAt
    ) {
    }

    private final class ConsumerWorker {
        private final ManagedConsumer managed;
        private volatile boolean running;
        private Thread thread;

        private ConsumerWorker(ManagedConsumer managed) {
            this.managed = managed;
        }

        private void start() {
            running = true;
            thread = new Thread(this::runLoop, "consumer-worker-" + managed.clientId());
            thread.setDaemon(true);
            thread.start();
            log.info("Consumer worker thread started: threadName={}", thread.getName());
        }

        private boolean running() {
            return running;
        }

        private void stop() {
            running = false;
            if (thread != null) {
                thread.interrupt();
                log.info("Consumer worker thread interrupted: threadName={}", thread.getName());
            }
        }

        private void runLoop() {
            try (KafkaConsumer<String, String> consumer = consumerOf(managed)) {
                consumer.subscribe(managed.topics());
                log.info("Consumer worker subscribed: clientId={}, topics={}", managed.clientId(), managed.topics());

                while (running) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                    for (ConsumerRecord<String, String> record : records) {
                        String msgId = managed.clientId() + "-" + record.topic() + "-" + record.partition() + "-" + record.offset();

                        Map<String, Object> payload = new HashMap<>();
                        payload.put("msgId", msgId);
                        payload.put("clientId", managed.clientId());
                        payload.put("groupId", managed.groupId());
                        payload.put("topic", record.topic());
                        payload.put("partition", record.partition());
                        payload.put("offset", record.offset());
                        payload.put("key", record.key() == null ? "" : record.key());
                        payload.put("value", record.value() == null ? "" : record.value());
                        payload.put("time", Instant.now().toString());
                        payload.put("fromPartitionGlobalId", "b" + record.leaderEpoch().orElse(0) + "_t" + record.topic() + "_p" + record.partition());
                        payload.put("toClientId", managed.clientId());
                        payload.put("toGroupId", managed.groupId());

                        labRealtimeWebSocketHandler.publish(LabRealtimeEventType.CONSUMER_MESSAGE, payload);
                        labRealtimeWebSocketHandler.publish(LabRealtimeEventType.CONSUMER_RECV, payload);
                        Map<String, Object> activity = labActivityLogService.append(
                            "info",
                            "CONSUMED",
                            "Consumer consumed message",
                            record.topic(),
                            Map.of(
                                "consumer", managed.clientId(),
                                "groupId", managed.groupId(),
                                "topic", record.topic(),
                                "partition", record.partition(),
                                "offset", record.offset(),
                                "key", record.key() == null ? "" : record.key(),
                                "value", record.value() == null ? "" : record.value()
                            )
                        );
                        labRealtimeWebSocketHandler.publish(LabRealtimeEventType.ACTIVITY_LOG, activity);
                    }
                }
            } catch (Exception ex) {
                running = false;
                log.error("Consumer worker failed: clientId={}, groupId={}", managed.clientId(), managed.groupId(), ex);
            }
        }
    }
}
