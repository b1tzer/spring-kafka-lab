package xpro.wang.kafkalab.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Web MVC infrastructure configuration. */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final @NonNull RunningEnvironmentInterceptor runningEnvironmentInterceptor;
    private final @NonNull OperationActivityInterceptor operationActivityInterceptor;

    public WebMvcConfig(@NonNull RunningEnvironmentInterceptor runningEnvironmentInterceptor,
            @NonNull OperationActivityInterceptor operationActivityInterceptor) {
        this.runningEnvironmentInterceptor = runningEnvironmentInterceptor;
        this.operationActivityInterceptor = operationActivityInterceptor;
    }

    /**
     * Registers global interceptors for protected API routes.
     *
     * @param registry
     *            interceptor registry
     */
    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(runningEnvironmentInterceptor).addPathPatterns("/topics/**", "/producer/**",
                "/consumer/**", "/cluster", "/brokers", "/dashboard", "/scenario/**");

        registry.addInterceptor(operationActivityInterceptor).addPathPatterns("/**");
    }
}
