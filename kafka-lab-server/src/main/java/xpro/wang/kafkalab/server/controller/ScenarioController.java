package xpro.wang.kafkalab.server.controller;

import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xpro.wang.kafkalab.server.model.ApiResponse;
import xpro.wang.kafkalab.server.model.ScenarioRunRequest;
import xpro.wang.kafkalab.server.service.ScenarioEngineService;

/** REST endpoints for scenario execution. */
@RestController
@RequestMapping("/scenario")
public class ScenarioController {

    private final ScenarioEngineService scenarioEngineService;

    public ScenarioController(ScenarioEngineService scenarioEngineService) {
        this.scenarioEngineService = scenarioEngineService;
    }

    /**
     * Executes a named experiment scenario.
     *
     * @param request
     *            scenario request payload
     * @return scenario execution response
     * @throws Exception
     *             when execution fails
     */
    @PostMapping("/run")
    public ApiResponse<Map<String, Object>> run(@Valid @RequestBody ScenarioRunRequest request) throws Exception {
        return ApiResponse.ok("Scenario executed", scenarioEngineService.run(request));
    }
}
