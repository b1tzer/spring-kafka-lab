package xpro.wang.kafkalab.server.model;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for stopping a managed consumer group simulation.
 *
 * @param groupId
 *            consumer group id
 */
public record ConsumerStopRequest(@NotBlank String groupId) {
}
