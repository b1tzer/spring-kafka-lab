package xpro.wang.kafkalab.server.model;

/**
 * Result object for environment lifecycle actions.
 *
 * @param instanceId environment instance id
 * @param action performed action name
 * @param success whether execution succeeded
 * @param output action command output
 */
public record EnvironmentActionResult(
        String instanceId,
        String action,
        boolean success,
        String output
) {
}
