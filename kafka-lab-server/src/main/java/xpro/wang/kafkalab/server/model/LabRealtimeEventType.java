package xpro.wang.kafkalab.server.model;

/**
 * Realtime websocket event categories.
 */
public enum LabRealtimeEventType {
    ENVIRONMENT_CHANGED,
    TOPIC_CHANGED,
    CONSUMER_CHANGED,
    PRODUCER_CHANGED
}
