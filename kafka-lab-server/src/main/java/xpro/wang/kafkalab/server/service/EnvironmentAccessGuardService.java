package xpro.wang.kafkalab.server.service;

import org.springframework.stereotype.Service;

/** Domain service for validating environment runtime preconditions. */
@Service
public class EnvironmentAccessGuardService {

    private final EnvironmentManagerService environmentManagerService;

    public EnvironmentAccessGuardService(EnvironmentManagerService environmentManagerService) {
        this.environmentManagerService = environmentManagerService;
    }

    /** Ensures at least one environment is running. */
    public void ensureRunningEnvironment() {
        environmentManagerService.getRunningEnvironment();
    }
}
