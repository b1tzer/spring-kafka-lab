package xpro.wang.kafkalab.server.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import xpro.wang.kafkalab.server.model.ApiResponse;
import xpro.wang.kafkalab.server.model.ConsumerStartRequest;
import xpro.wang.kafkalab.server.model.ConsumerStopRequest;
import xpro.wang.kafkalab.server.model.LabRealtimeAction;
import xpro.wang.kafkalab.server.model.LabRealtimeEventType;
import xpro.wang.kafkalab.server.service.ConsumerLabService;
import xpro.wang.kafkalab.server.service.LabRealtimeWebSocketHandler;

import java.util.Map;

/**
 * REST endpoints for consumer simulation operations.
 */
@RestController
@RequestMapping("/consumer")
public class ConsumerController {

    private final ConsumerLabService consumerLabService;
    private final LabRealtimeWebSocketHandler labRealtimeWebSocketHandler;

    public ConsumerController(ConsumerLabService consumerLabService,
            LabRealtimeWebSocketHandler labRealtimeWebSocketHandler) {
        this.consumerLabService = consumerLabService;
        this.labRealtimeWebSocketHandler = labRealtimeWebSocketHandler;
    }

    /**
     * Starts a managed consumer simulation.
     *
     * @param request start request
     * @return start result response
     */
    @PostMapping("/start")
    public ApiResponse<Map<String, Object>> start(@Valid @RequestBody ConsumerStartRequest request) {
        Map<String, Object> data = consumerLabService.startConsumer(request);
        labRealtimeWebSocketHandler.publish(LabRealtimeEventType.CONSUMER_CHANGED, Map.of(
            "action", LabRealtimeAction.STARTED.name(),
                "groupId", request.groupId(),
                "topic", request.topic()
        ));
        return ApiResponse.ok("Consumer started", data);
    }

    /**
     * Stops a managed consumer simulation.
     *
     * @param request stop request
     * @return stop result response
     */
    @PostMapping("/stop")
    public ApiResponse<Map<String, Object>> stop(@Valid @RequestBody ConsumerStopRequest request) {
        Map<String, Object> data = consumerLabService.stopConsumer(request.groupId());
        labRealtimeWebSocketHandler.publish(LabRealtimeEventType.CONSUMER_CHANGED, Map.of(
            "action", LabRealtimeAction.STOPPED.name(),
                "groupId", request.groupId()
        ));
        return ApiResponse.ok("Consumer stopped", data);
    }

    /**
     * Returns consumer group details.
     *
     * @return group details response
     * @throws Exception when query fails
     */
    @GetMapping("/groups")
    public ApiResponse<Map<String, Object>> groups() throws Exception {
        return ApiResponse.ok("Consumer groups fetched", consumerLabService.listGroups());
    }
}
