package xpro.wang.kafkalab.server.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for creating a Kafka environment instance.
 *
 * @param name display name
 * @param brokerCount broker count
 * @param replicationFactor replication factor
 * @param externalPortBase first broker external port
 * @param kafkaUiEnabled whether Kafka UI should be deployed
 * @param kafkaUiPort Kafka UI external port
 * @param kafkaImage Kafka broker image
 * @param kafkaUiImage Kafka UI image
 */
public record EnvironmentCreateRequest(
    @NotBlank String name,
    @Min(1) @Max(6) int brokerCount,
    @Min(1) @Max(6) int replicationFactor,
    @Min(10000) @Max(65000) int externalPortBase,
    Boolean kafkaUiEnabled,
    @Min(10000) @Max(65000) Integer kafkaUiPort,
    String kafkaImage,
    String kafkaUiImage) {}
