package xpro.wang.kafkalab.server.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xpro.wang.kafkalab.server.model.ApiResponse;
import xpro.wang.kafkalab.server.model.LabRealtimeAction;
import xpro.wang.kafkalab.server.model.LabRealtimeEventType;
import xpro.wang.kafkalab.server.model.ProducerRegisterRequest;
import xpro.wang.kafkalab.server.model.ProducerSendRequest;
import xpro.wang.kafkalab.server.model.SubscriptionTopicsUpdateRequest;
import xpro.wang.kafkalab.server.service.LabRealtimeWebSocketHandler;
import xpro.wang.kafkalab.server.service.ProducerLabService;

import java.util.List;
import java.util.Map;

/**
 * REST endpoints for producer operations.
 */
@RestController
@RequestMapping("/producer")
public class ProducerController {

    private final ProducerLabService producerLabService;
    private final LabRealtimeWebSocketHandler labRealtimeWebSocketHandler;

    public ProducerController(ProducerLabService producerLabService,
            LabRealtimeWebSocketHandler labRealtimeWebSocketHandler) {
        this.producerLabService = producerLabService;
        this.labRealtimeWebSocketHandler = labRealtimeWebSocketHandler;
    }

    /**
     * Sends one or more messages to a topic.
     *
     * @param request producer request payload
     * @return send result response
     * @throws Exception when send fails
     */
    @PostMapping("/send")
    public ApiResponse<Map<String, Object>> send(@Valid @RequestBody ProducerSendRequest request) throws Exception {
        Map<String, Object> result = producerLabService.sendWithMetadata(request);
        int sent = (int) result.getOrDefault("count", 0);
        labRealtimeWebSocketHandler.publish(LabRealtimeEventType.PRODUCER_CHANGED, Map.of(
            "action", LabRealtimeAction.SENT.name(),
                "topic", request.topic(),
                "count", sent
        ));
        return ApiResponse.ok("Messages sent", result);
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@Valid @RequestBody ProducerRegisterRequest request) {
        Map<String, Object> producer = producerLabService.registerManagedProducer(request);
        labRealtimeWebSocketHandler.publish(LabRealtimeEventType.PRODUCER_CHANGED, Map.of(
                "action", LabRealtimeAction.CREATED.name(),
                "producerId", producer.get("producerId")
        ));
        return ApiResponse.ok("Producer registered", producer);
    }

    @GetMapping("/managed")
    public ApiResponse<List<Map<String, Object>>> managed() {
        return ApiResponse.ok("Managed producers fetched", producerLabService.listManagedProducers());
    }

    @PostMapping("/{producerId}/send")
    public ApiResponse<Map<String, Object>> sendByProducer(
            @PathVariable String producerId,
            @Valid @RequestBody ProducerSendRequest request) throws Exception {
        Map<String, Object> result = producerLabService.sendByManagedProducer(producerId, request);
        int sent = (int) result.getOrDefault("count", 0);
        labRealtimeWebSocketHandler.publish(LabRealtimeEventType.PRODUCER_CHANGED, Map.of(
                "action", LabRealtimeAction.SENT.name(),
                "producerId", producerId,
                "topic", request.topic(),
                "count", sent
        ));
        return ApiResponse.ok("Messages sent by managed producer", result);
    }

    @PutMapping("/{producerId}/topics")
    public ApiResponse<Map<String, Object>> updateTopics(
            @PathVariable String producerId,
            @Valid @RequestBody SubscriptionTopicsUpdateRequest request) {
        Map<String, Object> producer = producerLabService.updateManagedProducerTopics(producerId, request.topics());
        labRealtimeWebSocketHandler.publish(LabRealtimeEventType.PRODUCER_CHANGED, Map.of(
            "action", LabRealtimeAction.UPDATED.name(),
                "producerId", producerId,
                "topics", request.topics()
        ));
        return ApiResponse.ok("Producer topics updated", producer);
    }

    @DeleteMapping("/{producerId}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable String producerId) {
        Map<String, Object> data = producerLabService.deleteManagedProducer(producerId);
        labRealtimeWebSocketHandler.publish(LabRealtimeEventType.PRODUCER_CHANGED, Map.of(
                "action", LabRealtimeAction.DELETED.name(),
                "producerId", producerId
        ));
        return ApiResponse.ok("Producer deleted", data);
    }
}
