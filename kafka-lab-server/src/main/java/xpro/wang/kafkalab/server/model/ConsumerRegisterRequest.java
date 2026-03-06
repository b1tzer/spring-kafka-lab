package xpro.wang.kafkalab.server.model;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request payload for registering a managed consumer client.
 *
 * @param groupId consumer group id, optional
 * @param clientId consumer client id, optional
 * @param hostIp host ip metadata
 * @param topics subscribed topics
 * @param autoCommit whether auto commit enabled
 */
public record ConsumerRegisterRequest(
    String groupId,
    String clientId,
    String hostIp,
    @NotEmpty @Size(min = 1) List<String> topics,
    Boolean autoCommit) {}
