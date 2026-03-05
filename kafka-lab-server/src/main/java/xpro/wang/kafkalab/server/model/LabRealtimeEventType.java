package xpro.wang.kafkalab.server.model;

/**
 * Realtime websocket event categories.
 */
public enum LabRealtimeEventType {
    ENVIRONMENT_CHANGED,
    TOPIC_CHANGED,
    CONSUMER_CHANGED,
    PRODUCER_CHANGED,
    ACTIVITY_LOG,
    ACTIVITY_LOG_SNAPSHOT,
    PRODUCER_MESSAGE,
    CONSUMER_MESSAGE,
    CONSUMER_RECV,
    TOPOLOGY_SNAPSHOT,
    PRODUCER_SEND
}
