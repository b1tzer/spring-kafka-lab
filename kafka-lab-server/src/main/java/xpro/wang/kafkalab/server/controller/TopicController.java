package xpro.wang.kafkalab.server.controller;

import jakarta.validation.Valid;
import java.util.Collection;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import xpro.wang.kafkalab.server.model.ApiResponse;
import xpro.wang.kafkalab.server.model.LabRealtimeAction;
import xpro.wang.kafkalab.server.model.LabRealtimeEventType;
import xpro.wang.kafkalab.server.model.TopicRequest;
import xpro.wang.kafkalab.server.service.LabRealtimeWebSocketHandler;
import xpro.wang.kafkalab.server.service.TopicAdminService;

/** REST endpoints for topic management. */
@RestController
@RequestMapping("/topics")
public class TopicController {

    private final TopicAdminService topicAdminService;
    private final LabRealtimeWebSocketHandler labRealtimeWebSocketHandler;

    public TopicController(TopicAdminService topicAdminService,
            LabRealtimeWebSocketHandler labRealtimeWebSocketHandler) {
        this.topicAdminService = topicAdminService;
        this.labRealtimeWebSocketHandler = labRealtimeWebSocketHandler;
    }

    /**
     * Creates a new topic.
     *
     * @param request
     *            topic create request
     * @return operation response
     * @throws Exception
     *             when create fails
     */
    @PostMapping
    public ApiResponse<Void> create(@Valid @RequestBody TopicRequest request) throws Exception {
        topicAdminService.createTopic(request);
        labRealtimeWebSocketHandler.publish(LabRealtimeEventType.TOPIC_CHANGED,
                Map.of("action", LabRealtimeAction.CREATED.name(), "topic", request.topicName()));
        return ApiResponse.ok("Topic created", null);
    }

    /**
     * Lists all topic names.
     *
     * @return topic list response
     * @throws Exception
     *             when query fails
     */
    @GetMapping
    public ApiResponse<Collection<String>> list() throws Exception {
        return ApiResponse.ok("Topics fetched", topicAdminService.listTopics());
    }

    /**
     * Returns detailed topic metadata.
     *
     * @param name
     *            topic name
     * @return topic detail response
     * @throws Exception
     *             when query fails
     */
    @GetMapping("/{name}")
    public ApiResponse<Map<String, Object>> describe(@PathVariable("name") String name) throws Exception {
        return ApiResponse.ok("Topic described", topicAdminService.describeTopic(name));
    }

    /**
     * Deletes a topic.
     *
     * @param name
     *            topic name
     * @return operation response
     * @throws Exception
     *             when delete fails
     */
    @DeleteMapping("/{name}")
    public ApiResponse<Void> delete(@PathVariable("name") String name) throws Exception {
        topicAdminService.deleteTopic(name);
        labRealtimeWebSocketHandler.publish(LabRealtimeEventType.TOPIC_CHANGED,
                Map.of("action", LabRealtimeAction.DELETED.name(), "topic", name));
        return ApiResponse.ok("Topic deleted", null);
    }
}
