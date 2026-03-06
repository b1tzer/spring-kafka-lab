package xpro.wang.kafkalab.server.model;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Request payload for updating subscribed topics.
 *
 * @param topics
 *            subscribed topics list
 */
public record SubscriptionTopicsUpdateRequest(@NotEmpty List<String> topics) {
}
