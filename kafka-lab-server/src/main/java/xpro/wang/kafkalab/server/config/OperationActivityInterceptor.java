package xpro.wang.kafkalab.server.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import xpro.wang.kafkalab.server.model.LabRealtimeEventType;
import xpro.wang.kafkalab.server.service.LabActivityLogService;
import xpro.wang.kafkalab.server.service.LabRealtimeWebSocketHandler;

/** Global interceptor for operation activity logging. */
@Component
public class OperationActivityInterceptor implements HandlerInterceptor {

    private final LabActivityLogService labActivityLogService;
    private final LabRealtimeWebSocketHandler labRealtimeWebSocketHandler;

    public OperationActivityInterceptor(LabActivityLogService labActivityLogService,
            LabRealtimeWebSocketHandler labRealtimeWebSocketHandler) {
        this.labActivityLogService = labActivityLogService;
        this.labRealtimeWebSocketHandler = labRealtimeWebSocketHandler;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull Object handler, @Nullable Exception ex) {
        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method)) {
            return;
        }

        String path = request.getRequestURI();
        if (path.startsWith("/actuator")) {
            return;
        }

        int status = response.getStatus();
        String level = status >= 400 ? "error" : "success";
        String action = method + " " + path;
        String detail = status >= 400 ? "Operation failed" : "Operation succeeded";

        Map<String, Object> fields = new HashMap<>();
        fields.put("method", method);
        fields.put("path", path);
        fields.put("status", status);
        if (ex != null && ex.getMessage() != null) {
            fields.put("error", ex.getMessage());
        }

        Map<String, Object> entry = labActivityLogService.append(level, action, detail, "", fields);
        labRealtimeWebSocketHandler.publish(LabRealtimeEventType.ACTIVITY_LOG, entry);
    }
}
