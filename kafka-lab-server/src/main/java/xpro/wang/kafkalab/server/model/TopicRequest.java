package xpro.wang.kafkalab.server.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * Request payload for creating a topic.
 *
 * @param topicName topic name
 * @param partitionCount partition count
 * @param replicationFactor replication factor
 * @param configs topic configs
 */
public record TopicRequest(
    @NotBlank String topicName,
    @Min(1) int partitionCount,
    @Min(1) short replicationFactor,
    Map<String, String> configs) {}
