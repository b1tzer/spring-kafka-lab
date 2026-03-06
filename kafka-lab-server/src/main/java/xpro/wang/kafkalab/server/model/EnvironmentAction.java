package xpro.wang.kafkalab.server.model;

/** Supported environment command actions. */
public enum EnvironmentAction {
  START("start"),
  STOP("stop"),
  DELETE("delete"),
  STATUS("status"),
  LOGS("logs");

  private final String value;

  EnvironmentAction(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
