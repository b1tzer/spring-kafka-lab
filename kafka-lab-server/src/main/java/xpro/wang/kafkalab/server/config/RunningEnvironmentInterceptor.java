package xpro.wang.kafkalab.server.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import xpro.wang.kafkalab.server.service.EnvironmentAccessGuardService;

/**
 * Intercepts protected Kafka APIs and verifies that at least one
 * environment is currently running.
 */
@Component
public class RunningEnvironmentInterceptor implements HandlerInterceptor {

    private final EnvironmentAccessGuardService environmentAccessGuardService;

    public RunningEnvironmentInterceptor(EnvironmentAccessGuardService environmentAccessGuardService) {
        this.environmentAccessGuardService = environmentAccessGuardService;
    }

    /**
     * Validates running environment precondition before request handling.
     *
     * @param request current request
     * @param response current response
     * @param handler selected handler
     * @return true when processing can continue
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        environmentAccessGuardService.ensureRunningEnvironment();
        return true;
    }
}
