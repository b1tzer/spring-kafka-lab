package xpro.wang.kafkalab.server.model;

/** Realtime action markers used in event payloads. */
public enum LabRealtimeAction {
  CREATED,
  UPDATED,
  STARTED,
  STOPPED,
  DELETED,
  SENT
}
