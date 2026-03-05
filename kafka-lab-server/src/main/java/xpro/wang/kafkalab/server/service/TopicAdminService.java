package xpro.wang.kafkalab.server.service;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.ConfigResource;
import org.springframework.stereotype.Service;
import xpro.wang.kafkalab.server.model.TopicRequest;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Topic administration service based on Kafka AdminClient.
 */
@Service
public class TopicAdminService {

    private final RuntimeKafkaConnectionService runtimeKafkaConnectionService;

    public TopicAdminService(RuntimeKafkaConnectionService runtimeKafkaConnectionService) {
        this.runtimeKafkaConnectionService = runtimeKafkaConnectionService;
    }

    /**
     * Creates a topic.
     *
     * @param request topic request
     * @throws Exception when creation fails
     */
    public void createTopic(TopicRequest request) throws Exception {
        NewTopic newTopic = new NewTopic(request.topicName(), request.partitionCount(), request.replicationFactor());
        if (request.configs() != null && !request.configs().isEmpty()) {
            newTopic.configs(request.configs());
        }
        try (AdminClient adminClient = adminClient()) {
            adminClient.createTopics(List.of(newTopic)).all().get();
        }
    }

    /**
     * Deletes a topic.
     *
     * @param topicName topic name
     * @throws Exception when deletion fails
     */
    public void deleteTopic(String topicName) throws Exception {
        try (AdminClient adminClient = adminClient()) {
            adminClient.deleteTopics(List.of(topicName)).all().get();
        }
    }

    /**
     * Lists topic names.
     *
     * @return topic names
     * @throws Exception when query fails
     */
    public Collection<String> listTopics() throws Exception {
        try (AdminClient adminClient = adminClient()) {
            return adminClient.listTopics().names().get();
        }
    }

    /**
     * Describes a topic including partitions and configs.
     *
     * @param topicName topic name
     * @return topic metadata map
     * @throws Exception when query fails
     */
    public Map<String, Object> describeTopic(String topicName) throws Exception {
        try (AdminClient adminClient = adminClient()) {
            DescribeTopicsResult describeTopicsResult = adminClient.describeTopics(List.of(topicName));
            var description = describeTopicsResult.topicNameValues().get(topicName).get();

            ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, topicName);
            Config config = adminClient.describeConfigs(List.of(resource)).all().get().get(resource);

            Map<String, String> configs = new HashMap<>();
            for (ConfigEntry entry : config.entries()) {
                configs.put(entry.name(), entry.value());
            }

            Map<String, Object> result = new HashMap<>();
            result.put("name", topicName);
            result.put("partitionCount", description.partitions().size());
            result.put("partitions", description.partitions().stream()
                    .map(p -> Map.of(
                            "partition", p.partition(),
                            "leader", p.leader() == null ? "N/A" : p.leader().idString(),
                            "replicas", p.replicas().stream().map(r -> r.idString()).collect(Collectors.toList())
                    ))
                    .toList());
            result.put("configs", configs);
            return result;
        }
    }

    private AdminClient adminClient() {
        return AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                runtimeKafkaConnectionService.bootstrapServers()
        ));
    }
}
