package xpro.wang.kafkalab.server.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import xpro.wang.kafkalab.server.model.ApiResponse;
import xpro.wang.kafkalab.server.model.EnvironmentActionResult;
import xpro.wang.kafkalab.server.model.EnvironmentCreateRequest;
import xpro.wang.kafkalab.server.model.EnvironmentInstance;
import xpro.wang.kafkalab.server.model.LabRealtimeAction;
import xpro.wang.kafkalab.server.model.LabRealtimeEventType;
import xpro.wang.kafkalab.server.service.EnvironmentManagerService;
import xpro.wang.kafkalab.server.service.LabRealtimeWebSocketHandler;

import java.util.List;

/**
 * REST endpoints for Kafka environment lifecycle management.
 */
@RestController
@RequestMapping("/env")
public class EnvironmentController {

    private final EnvironmentManagerService environmentManagerService;
    private final LabRealtimeWebSocketHandler labRealtimeWebSocketHandler;

    public EnvironmentController(EnvironmentManagerService environmentManagerService,
            LabRealtimeWebSocketHandler labRealtimeWebSocketHandler) {
        this.environmentManagerService = environmentManagerService;
        this.labRealtimeWebSocketHandler = labRealtimeWebSocketHandler;
    }

    /**
     * Creates a new environment instance.
     *
     * @param request environment create request
     * @return created environment response
     * @throws Exception when creation fails
     */
    @PostMapping("/create")
    public ApiResponse<EnvironmentInstance> create(@Valid @RequestBody EnvironmentCreateRequest request)
            throws Exception {
        EnvironmentInstance created = environmentManagerService.create(request);
        labRealtimeWebSocketHandler.publish(LabRealtimeEventType.ENVIRONMENT_CHANGED, java.util.Map.of(
                "action", LabRealtimeAction.CREATED.name(),
                "id", created.id(),
                "name", created.name()));
        return ApiResponse.ok("Environment created", created);
    }

    /**
     * Starts an environment instance.
     *
     * @param id instance id
     * @return action result response
     * @throws Exception when start fails
     */
    @PostMapping("/{id}/start")
    public ApiResponse<EnvironmentActionResult> start(@PathVariable String id) throws Exception {
        EnvironmentActionResult result = environmentManagerService.start(id);
        labRealtimeWebSocketHandler.publish(LabRealtimeEventType.ENVIRONMENT_CHANGED, java.util.Map.of(
                "action", LabRealtimeAction.STARTED.name(),
                "id", id,
                "success", result.success()));
        return ApiResponse.ok("Environment started", result);
    }

    /**
     * Stops an environment instance.
     *
     * @param id instance id
     * @return action result response
     * @throws Exception when stop fails
     */
    @PostMapping("/{id}/stop")
    public ApiResponse<EnvironmentActionResult> stop(@PathVariable String id) throws Exception {
        EnvironmentActionResult result = environmentManagerService.stop(id);
        labRealtimeWebSocketHandler.publish(LabRealtimeEventType.ENVIRONMENT_CHANGED, java.util.Map.of(
                "action", LabRealtimeAction.STOPPED.name(),
                "id", id,
                "success", result.success()));
        return ApiResponse.ok("Environment stopped", result);
    }

    /**
     * Deletes an environment instance.
     *
     * @param id instance id
     * @return action result response
     * @throws Exception when delete fails
     */
    @DeleteMapping("/{id}")
    public ApiResponse<EnvironmentActionResult> delete(@PathVariable String id) throws Exception {
        EnvironmentActionResult result = environmentManagerService.delete(id);
        labRealtimeWebSocketHandler.publish(LabRealtimeEventType.ENVIRONMENT_CHANGED, java.util.Map.of(
                "action", LabRealtimeAction.DELETED.name(),
                "id", id,
                "success", result.success()));
        return ApiResponse.ok("Environment deleted", result);
    }

    /**
     * Returns runtime status for an environment instance.
     *
     * @param id instance id
     * @return status response
     * @throws Exception when query fails
     */
    @GetMapping("/{id}/status")
    public ApiResponse<EnvironmentActionResult> status(@PathVariable String id) throws Exception {
        return ApiResponse.ok("Environment status fetched", environmentManagerService.status(id));
    }

    /**
     * Returns tail logs for an environment instance.
     *
     * @param id   instance id
     * @param tail line count
     * @return logs response
     * @throws Exception when query fails
     */
    @GetMapping("/{id}/logs")
    public ApiResponse<EnvironmentActionResult> logs(@PathVariable String id,
            @RequestParam(defaultValue = "200") int tail) throws Exception {
        return ApiResponse.ok("Environment logs fetched", environmentManagerService.logs(id, tail));
    }

    /**
     * Returns all tracked environment instances.
     *
     * @return environment list response
     */
    @GetMapping
    public ApiResponse<List<EnvironmentInstance>> list() {
        return ApiResponse.ok("Environment list fetched", environmentManagerService.list());
    }

    /**
     * Returns a single environment instance by id.
     *
     * @param id instance id
     * @return environment response
     */
    @GetMapping("/{id}")
    public ApiResponse<EnvironmentInstance> get(@PathVariable String id) {
        return ApiResponse.ok("Environment fetched", environmentManagerService.get(id));
    }
}
