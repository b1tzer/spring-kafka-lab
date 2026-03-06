package xpro.wang.kafkalab.server.model;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * Request payload for triggering a predefined scenario.
 *
 * @param scenarioName
 *            scenario name
 * @param parameters
 *            scenario parameter map
 */
public record ScenarioRunRequest(@NotBlank String scenarioName, Map<String, Object> parameters) {
}
