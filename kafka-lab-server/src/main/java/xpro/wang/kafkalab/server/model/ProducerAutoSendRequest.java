package xpro.wang.kafkalab.server.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for starting producer auto-send task.
 *
 * @param topic target topic for auto-send
 * @param frequencyPerSecond send frequency (messages/second)
 */
public record ProducerAutoSendRequest(
    @NotBlank String topic, @DecimalMin(value = "0.1") Double frequencyPerSecond) {}
