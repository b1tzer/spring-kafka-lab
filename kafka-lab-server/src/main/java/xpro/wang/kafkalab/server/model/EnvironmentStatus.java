package xpro.wang.kafkalab.server.model;

/**
 * Lifecycle status for environment instances.
 */
public enum EnvironmentStatus {
    CREATED,
    RUNNING,
    START_FAILED,
    STOPPED,
    STOP_FAILED
}
