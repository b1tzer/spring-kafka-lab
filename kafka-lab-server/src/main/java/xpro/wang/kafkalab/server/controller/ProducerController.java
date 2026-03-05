package xpro.wang.kafkalab.server.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xpro.wang.kafkalab.server.model.ApiResponse;
import xpro.wang.kafkalab.server.model.LabRealtimeAction;
import xpro.wang.kafkalab.server.model.LabRealtimeEventType;
import xpro.wang.kafkalab.server.model.ProducerSendRequest;
import xpro.wang.kafkalab.server.service.LabRealtimeWebSocketHandler;
import xpro.wang.kafkalab.server.service.ProducerLabService;

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
        int sent = producerLabService.send(request);
        labRealtimeWebSocketHandler.publish(LabRealtimeEventType.PRODUCER_CHANGED, Map.of(
            "action", LabRealtimeAction.SENT.name(),
                "topic", request.topic(),
                "count", sent
        ));
        return ApiResponse.ok("Messages sent", Map.of("count", sent, "topic", request.topic()));
    }
}
