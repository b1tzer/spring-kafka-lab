package xpro.wang.kafkalab.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC infrastructure configuration.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RunningEnvironmentInterceptor runningEnvironmentInterceptor;

    public WebMvcConfig(RunningEnvironmentInterceptor runningEnvironmentInterceptor) {
        this.runningEnvironmentInterceptor = runningEnvironmentInterceptor;
    }

    /**
     * Registers global interceptors for protected API routes.
     *
     * @param registry interceptor registry
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(runningEnvironmentInterceptor)
                .addPathPatterns(
                        "/topics/**",
                        "/producer/**",
                        "/consumer/**",
                        "/cluster",
                        "/brokers",
                        "/dashboard",
                        "/scenario/**"
                );
    }
}
