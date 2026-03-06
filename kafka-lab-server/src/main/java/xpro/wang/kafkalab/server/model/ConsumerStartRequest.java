package xpro.wang.kafkalab.server.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for starting a consumer group simulation.
 *
 * @param groupId consumer group id
 * @param topic topic name
 * @param concurrency consumer concurrency level
 * @param autoCommit whether auto commit is enabled
 */
public record ConsumerStartRequest(
    @NotBlank String groupId,
    @NotBlank String topic,
    @Min(1) Integer concurrency,
    Boolean autoCommit) {}
