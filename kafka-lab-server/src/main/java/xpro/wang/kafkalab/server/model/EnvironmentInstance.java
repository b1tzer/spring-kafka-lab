package xpro.wang.kafkalab.server.model;

import java.time.Instant;

/**
 * Persisted environment instance metadata.
 *
 * @param id unique instance id
 * @param name display name
 * @param projectName compose project name
 * @param brokerCount broker count
 * @param replicationFactor replication factor
 * @param bootstrapServers externally reachable bootstrap servers
 * @param kafkaUiEnabled whether Kafka UI is enabled
 * @param kafkaUiPort Kafka UI external port
 * @param composeFilePath compose file absolute path
 * @param status lifecycle status
 * @param createdAt creation timestamp
 * @param updatedAt last update timestamp
 */
public record EnvironmentInstance(
        String id,
        String name,
        String projectName,
        int brokerCount,
        int replicationFactor,
        String bootstrapServers,
        boolean kafkaUiEnabled,
        Integer kafkaUiPort,
        String composeFilePath,
        EnvironmentStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
