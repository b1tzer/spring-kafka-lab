package xpro.wang.kafkalab.server.service;

import org.springframework.stereotype.Service;
import xpro.wang.kafkalab.server.model.EnvironmentInstance;

/**
 * Provides runtime Kafka connection info based on the active environment.
 */
@Service
public class RuntimeKafkaConnectionService {

    private final EnvironmentManagerService environmentManagerService;

    public RuntimeKafkaConnectionService(EnvironmentManagerService environmentManagerService) {
        this.environmentManagerService = environmentManagerService;
    }

    /**
     * Returns currently running environment metadata.
     *
     * @return running environment instance
     */
    public EnvironmentInstance runningEnvironment() {
        return environmentManagerService.getRunningEnvironment();
    }

    /**
     * Returns bootstrap server list for Kafka client connections.
     *
     * @return bootstrap server string
     */
    public String bootstrapServers() {
        return runningEnvironment().bootstrapServers();
    }
}
