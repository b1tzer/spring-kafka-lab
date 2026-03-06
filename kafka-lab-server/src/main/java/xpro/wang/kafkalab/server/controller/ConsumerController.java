package xpro.wang.kafkalab.server.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import xpro.wang.kafkalab.server.model.ApiResponse;
import xpro.wang.kafkalab.server.model.ConsumerRegisterRequest;
import xpro.wang.kafkalab.server.model.ConsumerStartRequest;
import xpro.wang.kafkalab.server.model.ConsumerStopRequest;
import xpro.wang.kafkalab.server.model.LabRealtimeAction;
import xpro.wang.kafkalab.server.model.LabRealtimeEventType;
import xpro.wang.kafkalab.server.model.SubscriptionTopicsUpdateRequest;
import xpro.wang.kafkalab.server.service.ConsumerLabService;
import xpro.wang.kafkalab.server.service.LabRealtimeWebSocketHandler;

/** REST endpoints for consumer simulation operations. */
@RestController
@RequestMapping("/consumer")
public class ConsumerController {

  private final ConsumerLabService consumerLabService;
  private final LabRealtimeWebSocketHandler labRealtimeWebSocketHandler;

  public ConsumerController(
      ConsumerLabService consumerLabService,
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
    labRealtimeWebSocketHandler.publish(
        LabRealtimeEventType.CONSUMER_CHANGED,
        Map.of(
            "action",
            LabRealtimeAction.STARTED.name(),
            "groupId",
            request.groupId(),
            "topic",
            request.topic()));
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
    labRealtimeWebSocketHandler.publish(
        LabRealtimeEventType.CONSUMER_CHANGED,
        Map.of("action", LabRealtimeAction.STOPPED.name(), "groupId", request.groupId()));
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

  @PostMapping("/register")
  public ApiResponse<Map<String, Object>> register(
      @Valid @RequestBody ConsumerRegisterRequest request) {
    Map<String, Object> data = consumerLabService.registerConsumer(request);
    labRealtimeWebSocketHandler.publish(
        LabRealtimeEventType.CONSUMER_CHANGED,
        Map.of(
            "action", LabRealtimeAction.CREATED.name(),
            "clientId", data.get("clientId"),
            "groupId", data.get("groupId")));
    return ApiResponse.ok("Consumer registered", data);
  }

  @GetMapping("/managed")
  public ApiResponse<List<Map<String, Object>>> managed() {
    return ApiResponse.ok("Managed consumers fetched", consumerLabService.listManagedConsumers());
  }

  @PostMapping("/{clientId}/start")
  public ApiResponse<Map<String, Object>> startByClientId(@PathVariable String clientId) {
    Map<String, Object> data = consumerLabService.startByClientId(clientId);
    labRealtimeWebSocketHandler.publish(
        LabRealtimeEventType.CONSUMER_CHANGED,
        Map.of(
            "action", LabRealtimeAction.STARTED.name(),
            "clientId", clientId,
            "groupId", data.get("groupId")));
    return ApiResponse.ok("Consumer started", data);
  }

  @PostMapping("/{clientId}/stop")
  public ApiResponse<Map<String, Object>> stopByClientId(@PathVariable String clientId) {
    Map<String, Object> data = consumerLabService.stopByClientId(clientId);
    labRealtimeWebSocketHandler.publish(
        LabRealtimeEventType.CONSUMER_CHANGED,
        Map.of(
            "action", LabRealtimeAction.STOPPED.name(),
            "clientId", clientId,
            "groupId", data.get("groupId")));
    return ApiResponse.ok("Consumer stopped", data);
  }

  @PutMapping("/{clientId}/topics")
  public ApiResponse<Map<String, Object>> updateTopics(
      @PathVariable String clientId, @Valid @RequestBody SubscriptionTopicsUpdateRequest request) {
    Map<String, Object> data = consumerLabService.updateConsumerTopics(clientId, request.topics());
    labRealtimeWebSocketHandler.publish(
        LabRealtimeEventType.CONSUMER_CHANGED,
        Map.of(
            "action",
            LabRealtimeAction.UPDATED.name(),
            "clientId",
            clientId,
            "topics",
            request.topics()));
    return ApiResponse.ok("Consumer topics updated", data);
  }

  @DeleteMapping("/{clientId}")
  public ApiResponse<Map<String, Object>> delete(@PathVariable String clientId) {
    Map<String, Object> data = consumerLabService.deleteByClientId(clientId);
    labRealtimeWebSocketHandler.publish(
        LabRealtimeEventType.CONSUMER_CHANGED,
        Map.of("action", LabRealtimeAction.DELETED.name(), "clientId", clientId));
    return ApiResponse.ok("Consumer deleted", data);
  }
}
