package xpro.wang.kafkalab.server.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for producer send operation.
 *
 * @param topic target topic
 * @param key message key
 * @param message message payload
 * @param partition target partition
 * @param delay delay between batch sends in milliseconds
 * @param count message count
 * @param transactional whether to use transactional sending
 */
public record ProducerSendRequest(
    @NotBlank String topic,
    String key,
    @NotBlank String message,
    Integer partition,
    @Min(0) Long delay,
    @Min(1) Integer count,
    boolean transactional) {}
