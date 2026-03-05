package xpro.wang.kafkalab.server.service;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.springframework.stereotype.Service;
import xpro.wang.kafkalab.server.model.ConsumerRuntimeStatus;
import xpro.wang.kafkalab.server.model.ConsumerStartRequest;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consumer simulation service and group query utility.
 */
@Service
public class ConsumerLabService {

    private final RuntimeKafkaConnectionService runtimeKafkaConnectionService;
    private final Map<String, Map<String, Object>> localConsumerRegistry = new ConcurrentHashMap<>();

    public ConsumerLabService(RuntimeKafkaConnectionService runtimeKafkaConnectionService) {
        this.runtimeKafkaConnectionService = runtimeKafkaConnectionService;
    }

    /**
     * Registers a simulated consumer runtime entry.
     *
     * @param request start request
     * @return consumer runtime metadata
     */
    public Map<String, Object> startConsumer(ConsumerStartRequest request) {
        Map<String, Object> info = new HashMap<>();
        info.put("groupId", request.groupId());
        info.put("topic", request.topic());
        info.put("concurrency", request.concurrency() == null ? 1 : request.concurrency());
        info.put("autoCommit", request.autoCommit() == null || request.autoCommit());
        info.put("startedAt", Instant.now().toString());
        info.put("status", ConsumerRuntimeStatus.RUNNING.name());
        localConsumerRegistry.put(request.groupId(), info);
        return info;
    }

    /**
     * Unregisters a simulated consumer runtime entry.
     *
     * @param groupId consumer group id
     * @return stop result metadata
     */
    public Map<String, Object> stopConsumer(String groupId) {
        Map<String, Object> removed = localConsumerRegistry.remove(groupId);
        if (removed == null) {
            return Map.of("groupId", groupId, "status", ConsumerRuntimeStatus.NOT_FOUND.name());
        }
        return Map.of("groupId", groupId, "status", ConsumerRuntimeStatus.STOPPED.name());
    }

    /**
     * Returns consumer groups from Kafka and local registry metadata.
     *
     * @return group overview map
     * @throws Exception when query fails
     */
    public Map<String, Object> listGroups() throws Exception {
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
            item.put("managedByLab", localConsumerRegistry.containsKey(group));
            return item;
            }).toList();

            return Map.of(
                "groups", items,
                "localRegistry", localConsumerRegistry.values()
            );
        }
        }

        public List<Map<String, Object>> localConsumers() {
        return localConsumerRegistry.values().stream()
                .map(item -> {
                    Map<String, Object> copy = new HashMap<>();
                    copy.putAll(item);
                    return copy;
                })
                .toList();
    }

        private AdminClient adminClient() {
        return AdminClient.create(Map.of(
            AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
            runtimeKafkaConnectionService.bootstrapServers()
        ));
    }
}
