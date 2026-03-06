package xpro.wang.kafkalab.server.model;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Request payload for registering a managed producer.
 *
 * @param producerId
 *            producer client id, optional
 * @param topics
 *            subscribed topics for sending scope
 */
public record ProducerRegisterRequest(String producerId, @NotEmpty List<String> topics) {
}
