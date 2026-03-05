package xpro.wang.kafkalab.server.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Centralized activity log store for websocket broadcasting and future extensions.
 */
@Service
public class LabActivityLogService {

    private static final int MAX_LOG_SIZE = 500;

    private final Deque<Map<String, Object>> logs = new ArrayDeque<>();

    public synchronized Map<String, Object> append(
            String level,
            String action,
            String detail,
            String topic,
            Map<String, Object> fields
    ) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("id", Instant.now().toEpochMilli() + "-" + Math.random());
        entry.put("timestamp", System.currentTimeMillis());
        entry.put("time", Instant.now().toString());
        entry.put("level", level);
        entry.put("action", action);
        entry.put("detail", detail);
        entry.put("topic", topic == null ? "" : topic);
        entry.put("fields", fields == null ? Map.of() : new HashMap<>(fields));

        logs.addFirst(entry);
        while (logs.size() > MAX_LOG_SIZE) {
            logs.pollLast();
        }
        return entry;
    }

    public synchronized List<Map<String, Object>> recent(int limit) {
        int max = Math.max(1, limit);
        List<Map<String, Object>> all = new ArrayList<>(logs);
        if (all.size() <= max) {
            return all;
        }
        return all.subList(0, max);
    }
}
