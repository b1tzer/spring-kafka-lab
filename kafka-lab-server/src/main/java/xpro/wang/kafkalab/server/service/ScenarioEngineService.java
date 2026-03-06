package xpro.wang.kafkalab.server.service;

import java.util.Map;
import org.springframework.stereotype.Service;
import xpro.wang.kafkalab.server.model.ConsumerStartRequest;
import xpro.wang.kafkalab.server.model.ProducerSendRequest;
import xpro.wang.kafkalab.server.model.ScenarioRunRequest;
import xpro.wang.kafkalab.server.model.ScenarioStatus;

/** Scenario orchestration service. */
@Service
public class ScenarioEngineService {

  private final ProducerLabService producerLabService;
  private final ConsumerLabService consumerLabService;

  public ScenarioEngineService(
      ProducerLabService producerLabService, ConsumerLabService consumerLabService) {
    this.producerLabService = producerLabService;
    this.consumerLabService = consumerLabService;
  }

  /**
   * Executes a named scenario with optional parameters.
   *
   * @param request scenario request
   * @return execution details
   * @throws Exception when scenario fails
   */
  public Map<String, Object> run(ScenarioRunRequest request) throws Exception {
    String scenarioName = request.scenarioName().trim().toLowerCase();
    Map<String, Object> parameters = request.parameters() == null ? Map.of() : request.parameters();

    return switch (scenarioName) {
      case "rebalance" -> runRebalance(parameters);
      case "consumer-lag", "lag" -> runLag(parameters);
      case "poison-message", "dlq" -> runPoison(parameters);
      case "broker-crash" -> Map.of(
          "scenario", "broker-crash",
          "status", ScenarioStatus.MANUAL_STEP_REQUIRED.name(),
          "hint", "Use docker compose stop kafka2 then observe leader election");
      case "exactly-once" -> runExactlyOnce(parameters);
      default -> Map.of(
          "scenario", request.scenarioName(),
          "status", ScenarioStatus.NOT_SUPPORTED.name(),
          "supported",
              new String[] {
                "rebalance", "consumer-lag", "poison-message", "broker-crash", "exactly-once"
              });
    };
  }

  private Map<String, Object> runRebalance(Map<String, Object> parameters) {
    String topic = stringParam(parameters, "topic", "lab-rebalance-topic");
    String groupId = stringParam(parameters, "groupId", "lab-rebalance-group");

    consumerLabService.startConsumer(new ConsumerStartRequest(groupId, topic, 1, true));
    consumerLabService.startConsumer(new ConsumerStartRequest(groupId, topic, 2, true));

    return Map.of(
        "scenario",
        "rebalance",
        "status",
        ScenarioStatus.TRIGGERED.name(),
        "groupId",
        groupId,
        "topic",
        topic,
        "hint",
        "Check consumer group assignment changes in Kafka UI");
  }

  private Map<String, Object> runLag(Map<String, Object> parameters) throws Exception {
    String topic = stringParam(parameters, "topic", "lab-lag-topic");
    int count = intParam(parameters, "count", 10000);

    producerLabService.send(
        new ProducerSendRequest(topic, null, "lag-message", null, 0L, count, false));

    return Map.of(
        "scenario",
        "consumer-lag",
        "status",
        ScenarioStatus.TRIGGERED.name(),
        "topic",
        topic,
        "messageCount",
        count,
        "hint",
        "Start slow consumer and observe lag metrics");
  }

  private Map<String, Object> runPoison(Map<String, Object> parameters) {
    return Map.of(
        "scenario",
        "poison-message",
        "status",
        ScenarioStatus.MANUAL_STEP_REQUIRED.name(),
        "hint",
        "Implement listener exception + retry + DLT topic in your consumer module");
  }

  private Map<String, Object> runExactlyOnce(Map<String, Object> parameters) throws Exception {
    String topic = stringParam(parameters, "topic", "lab-eos-topic");
    producerLabService.send(
        new ProducerSendRequest(topic, "eos-key", "eos-message", null, 0L, 3, true));
    return Map.of(
        "scenario",
        "exactly-once",
        "status",
        ScenarioStatus.TRIGGERED.name(),
        "topic",
        topic,
        "hint",
        "Verify no duplicate messages under retries");
  }

  private String stringParam(Map<String, Object> parameters, String key, String defaultValue) {
    Object value = parameters.get(key);
    if (value == null) {
      return defaultValue;
    }
    return String.valueOf(value);
  }

  private int intParam(Map<String, Object> parameters, String key, int defaultValue) {
    Object value = parameters.get(key);
    if (value instanceof Number number) {
      return number.intValue();
    }
    if (value instanceof String text) {
      return Integer.parseInt(text);
    }
    return defaultValue;
  }
}
