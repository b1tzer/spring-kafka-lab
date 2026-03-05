package xpro.wang.kafkalab.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import xpro.wang.kafkalab.server.model.EnvironmentAction;
import xpro.wang.kafkalab.server.model.EnvironmentActionResult;
import xpro.wang.kafkalab.server.model.EnvironmentCreateRequest;
import xpro.wang.kafkalab.server.model.EnvironmentInstance;
import xpro.wang.kafkalab.server.model.EnvironmentStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Environment lifecycle management service backed by Docker Compose.
 */
@Service
public class EnvironmentManagerService {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentManagerService.class);

    private static final String DEFAULT_CLUSTER_ID = "MkU3OEVBNTcwNTJENDM2Qk";
    private static final int DEFAULT_KAFKA_UI_PORT = 18085;

    private final ObjectMapper objectMapper;
    private final Path envRoot;
    private final DockerComposeCommandService dockerComposeCommandService;
    private final EnvironmentResourceCleanupService environmentResourceCleanupService;
    private final Map<String, EnvironmentInstance> instances = new ConcurrentHashMap<>();

    public EnvironmentManagerService(ObjectMapper objectMapper,
            @Value("${kafka-lab.env.root:./runtime/environments}") String envRoot,
            DockerComposeCommandService dockerComposeCommandService,
            EnvironmentResourceCleanupService environmentResourceCleanupService) throws IOException {
        this.objectMapper = objectMapper;
        this.envRoot = Paths.get(envRoot).toAbsolutePath().normalize();
        this.dockerComposeCommandService = dockerComposeCommandService;
        this.environmentResourceCleanupService = environmentResourceCleanupService;
        Files.createDirectories(this.envRoot);
        loadPersistedInstances();
    }

    public List<EnvironmentInstance> list() {
        return instances.values().stream()
                .sorted(Comparator.comparing(EnvironmentInstance::createdAt).reversed())
                .toList();
    }

    /**
     * Returns environment instance by id.
     *
     * @param id instance id
     * @return environment instance
     */
    public EnvironmentInstance get(String id) {
        EnvironmentInstance instance = instances.get(id);
        if (instance == null) {
            throw new IllegalArgumentException("Environment not found: " + id);
        }
        return instance;
    }

    /**
     * Returns latest running environment instance.
     *
     * @return running environment instance
     */
    public EnvironmentInstance getRunningEnvironment() {
        return instances.values().stream()
            .filter(instance -> instance.status() == EnvironmentStatus.RUNNING)
                .max(Comparator.comparing(EnvironmentInstance::updatedAt))
                .orElseThrow(() -> new IllegalStateException(
                        "No running Kafka environment. Please create and start an environment in Environments page first."));
    }

    /**
     * Creates a new environment definition and compose file.
     *
     * @param request create request
     * @return created environment instance
     * @throws IOException when persistence fails
     */
    public synchronized EnvironmentInstance create(EnvironmentCreateRequest request) throws IOException {
        validateRequest(request);

        String id = UUID.randomUUID().toString().substring(0, 8);
        String projectName = "kafkalab-" + sanitize(request.name()) + "-" + id;
        Path instanceDir = envRoot.resolve(projectName);
        Files.createDirectories(instanceDir);

        Path composeFile = instanceDir.resolve("docker-compose.yml");
        String compose = renderCompose(request, projectName);
        Files.writeString(composeFile, compose, StandardCharsets.UTF_8);

        EnvironmentInstance instance = new EnvironmentInstance(
                id,
                request.name(),
                projectName,
                request.brokerCount(),
                request.replicationFactor(),
                externalBootstrapServers(request.externalPortBase(), request.brokerCount()),
                isKafkaUiEnabled(request),
                isKafkaUiEnabled(request) ? resolveKafkaUiPort(request) : null,
                composeFile.toString(),
                EnvironmentStatus.CREATED,
                Instant.now(),
                Instant.now());

        instances.put(id, instance);
        persist(instance);
        return instance;
    }

    /**
     * Starts an environment instance.
     *
     * @param id instance id
     * @return action result
     * @throws Exception when command execution fails
     */
    public synchronized EnvironmentActionResult start(String id) throws Exception {
        EnvironmentInstance instance = get(id);
        DockerComposeCommandService.CommandResult result = runCompose(instance, List.of("up", "-d"));
        updateStatus(id, result.success() ? EnvironmentStatus.RUNNING : EnvironmentStatus.START_FAILED);
        return new EnvironmentActionResult(id, EnvironmentAction.START.value(), result.success(), result.output());
    }

    /**
     * Stops an environment instance.
     *
     * @param id instance id
     * @return action result
     * @throws Exception when command execution fails
     */
    public synchronized EnvironmentActionResult stop(String id) throws Exception {
        EnvironmentInstance instance = get(id);
        DockerComposeCommandService.CommandResult result = runCompose(instance, List.of("stop"));
        updateStatus(id, result.success() ? EnvironmentStatus.STOPPED : EnvironmentStatus.STOP_FAILED);
        return new EnvironmentActionResult(id, EnvironmentAction.STOP.value(), result.success(), result.output());
    }

    /**
     * Deletes an environment instance and associated files.
     *
     * @param id instance id
     * @return action result
     * @throws Exception when command execution fails
     */
    public synchronized EnvironmentActionResult delete(String id) throws Exception {
        EnvironmentInstance instance = get(id);
        EnvironmentResourceCleanupService.CleanupResult result = environmentResourceCleanupService.cleanup(instance,
                true);
        if (result.success()) {
            instances.remove(id);
        }
        return new EnvironmentActionResult(id, EnvironmentAction.DELETE.value(), result.success(), result.output());
    }

    /**
     * Queries runtime status for an environment instance.
     *
     * @param id instance id
     * @return action result
     * @throws Exception when command execution fails
     */
    public synchronized EnvironmentActionResult status(String id) throws Exception {
        EnvironmentInstance instance = get(id);
        DockerComposeCommandService.CommandResult result = runCompose(instance, List.of("ps"));
        if (result.success() && result.output().toLowerCase(Locale.ROOT).contains("up")) {
            updateStatus(id, EnvironmentStatus.RUNNING);
        }
        return new EnvironmentActionResult(id, EnvironmentAction.STATUS.value(), result.success(), result.output());
    }

    /**
     * Retrieves environment logs.
     *
     * @param id   instance id
     * @param tail number of lines
     * @return action result
     * @throws Exception when command execution fails
     */
    public EnvironmentActionResult logs(String id, int tail) throws Exception {
        EnvironmentInstance instance = get(id);
        DockerComposeCommandService.CommandResult result = runCompose(instance,
                List.of("logs", "--tail", String.valueOf(tail)));
        return new EnvironmentActionResult(id, EnvironmentAction.LOGS.value(), result.success(), result.output());
    }

    @PreDestroy
    public synchronized void destroyAllGeneratedDockerResourcesOnShutdown() {
        if (instances.isEmpty()) {
            return;
        }

        List<EnvironmentInstance> snapshot = new ArrayList<>(instances.values());
        for (EnvironmentInstance instance : snapshot) {
            try {
                EnvironmentResourceCleanupService.CleanupResult result = environmentResourceCleanupService
                        .cleanup(instance, true);
                if (!result.success()) {
                    log.warn("Failed to cleanup docker resources for environment {}: {}", instance.id(),
                            result.output());
                    continue;
                }
            } catch (Exception ex) {
                log.warn("Error while cleaning environment {} on shutdown: {}", instance.id(), ex.getMessage());
            }
        }

        instances.clear();
        log.info("Shutdown cleanup completed for {} generated Kafka environments", snapshot.size());
    }

    private void validateRequest(EnvironmentCreateRequest request) {
        boolean kafkaUiEnabled = isKafkaUiEnabled(request);
        int kafkaUiPort = resolveKafkaUiPort(request);
        if (request.replicationFactor() > request.brokerCount()) {
            throw new IllegalArgumentException("replicationFactor must be <= brokerCount");
        }
        if (request.externalPortBase() + request.brokerCount() - 1 > 65535) {
            throw new IllegalArgumentException("externalPortBase + brokerCount - 1 must be <= 65535");
        }
        if (kafkaUiEnabled) {
            int first = request.externalPortBase();
            int last = request.externalPortBase() + request.brokerCount() - 1;
            if (kafkaUiPort >= first && kafkaUiPort <= last) {
                throw new IllegalArgumentException("kafkaUiPort conflicts with broker external port range");
            }
        }
    }

    private void updateStatus(String id, EnvironmentStatus status) throws IOException {
        EnvironmentInstance old = get(id);
        EnvironmentInstance updated = new EnvironmentInstance(
                old.id(),
                old.name(),
                old.projectName(),
                old.brokerCount(),
                old.replicationFactor(),
                old.bootstrapServers(),
                old.kafkaUiEnabled(),
                old.kafkaUiPort(),
                old.composeFilePath(),
                status,
                old.createdAt(),
                Instant.now());
        instances.put(id, updated);
        persist(updated);
    }

    private void persist(EnvironmentInstance instance) throws IOException {
        Path dir = Path.of(instance.composeFilePath()).getParent();
        if (dir == null) {
            return;
        }
        Path meta = dir.resolve("meta.json");
        Files.writeString(meta, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(instance),
                StandardCharsets.UTF_8);
    }

    private void loadPersistedInstances() throws IOException {
        if (!Files.exists(envRoot)) {
            return;
        }
        try (var stream = Files.list(envRoot)) {
            for (Path dir : stream.filter(Files::isDirectory).toList()) {
                Path meta = dir.resolve("meta.json");
                if (!Files.exists(meta)) {
                    continue;
                }
                EnvironmentInstance instance = objectMapper.readValue(Files.readString(meta),
                        EnvironmentInstance.class);
                instances.put(instance.id(), instance);
            }
        }
    }

    private DockerComposeCommandService.CommandResult runCompose(EnvironmentInstance instance,
            List<String> actionArgs) {
        Path composeFile = Path.of(instance.composeFilePath());
        return dockerComposeCommandService.runCompose(composeFile, instance.projectName(), actionArgs);
    }

    private String renderCompose(EnvironmentCreateRequest request, String projectName) {
        boolean kafkaUiEnabled = isKafkaUiEnabled(request);
        int kafkaUiPort = resolveKafkaUiPort(request);

        String kafkaImage = request.kafkaImage() == null || request.kafkaImage().isBlank()
                ? "confluentinc/cp-kafka:7.5.0"
                : request.kafkaImage();

        String kafkaUiImage = request.kafkaUiImage() == null || request.kafkaUiImage().isBlank()
                ? "provectuslabs/kafka-ui:latest"
                : request.kafkaUiImage();

        String voters = voters(request.brokerCount());

        StringBuilder sb = new StringBuilder();
        sb.append("version: '3.8'\n\nservices:\n");

        for (int i = 1; i <= request.brokerCount(); i++) {
            int port = request.externalPortBase() + (i - 1);
            sb.append("  kafka").append(i).append(":\n")
                    .append("    image: ").append(kafkaImage).append("\n")
                    .append("    hostname: kafka").append(i).append("\n")
                    .append("    ports:\n")
                    .append("      - \"").append(port).append(":").append(port).append("\"\n")
                    .append("    environment:\n")
                    .append("      KAFKA_NODE_ID: ").append(i).append("\n")
                    .append("      KAFKA_PROCESS_ROLES: broker,controller\n")
                    .append("      KAFKA_LISTENERS: INTERNAL://kafka").append(i).append(":9092,EXTERNAL://0.0.0.0:")
                    .append(port).append(",CONTROLLER://kafka").append(i).append(":9093\n")
                    .append("      KAFKA_ADVERTISED_LISTENERS: INTERNAL://kafka").append(i)
                    .append(":9092,EXTERNAL://localhost:").append(port).append("\n")
                    .append("      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER\n")
                    .append("      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,INTERNAL:PLAINTEXT,EXTERNAL:PLAINTEXT\n")
                    .append("      KAFKA_CONTROLLER_QUORUM_VOTERS: ").append(voters).append("\n")
                    .append("      KAFKA_INTER_BROKER_LISTENER_NAME: INTERNAL\n")
                    .append("      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: ").append(request.replicationFactor())
                    .append("\n")
                    .append("      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: ")
                    .append(request.replicationFactor()).append("\n")
                    .append("      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1\n")
                    .append("      KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0\n")
                    .append("      CLUSTER_ID: ").append(DEFAULT_CLUSTER_ID).append("\n\n");
        }

        if (kafkaUiEnabled) {
            sb.append("  kafka-ui:\n")
                    .append("    image: ").append(kafkaUiImage).append("\n")
                    .append("    ports:\n")
                    .append("      - \"").append(kafkaUiPort).append(":8080\"\n")
                    .append("    depends_on:\n");
            for (int i = 1; i <= request.brokerCount(); i++) {
                sb.append("      - kafka").append(i).append("\n");
            }
            sb.append("    environment:\n")
                    .append("      KAFKA_CLUSTERS_0_NAME: ").append(projectName).append("\n")
                    .append("      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: ")
                    .append(internalBootstrapServers(request.brokerCount())).append("\n\n");
        }

        return sb.toString();
    }

    private boolean isKafkaUiEnabled(EnvironmentCreateRequest request) {
        return request.kafkaUiEnabled() == null || request.kafkaUiEnabled();
    }

    private int resolveKafkaUiPort(EnvironmentCreateRequest request) {
        return request.kafkaUiPort() == null ? DEFAULT_KAFKA_UI_PORT : request.kafkaUiPort();
    }

    private String voters(int brokerCount) {
        List<String> voters = new ArrayList<>();
        for (int i = 1; i <= brokerCount; i++) {
            voters.add(i + "@kafka" + i + ":9093");
        }
        return String.join(",", voters);
    }

    private String internalBootstrapServers(int brokerCount) {
        List<String> brokers = new ArrayList<>();
        for (int i = 1; i <= brokerCount; i++) {
            brokers.add("kafka" + i + ":9092");
        }
        return String.join(",", brokers);
    }

    private String externalBootstrapServers(int externalPortBase, int brokerCount) {
        List<String> brokers = new ArrayList<>();
        for (int i = 0; i < brokerCount; i++) {
            brokers.add("localhost:" + (externalPortBase + i));
        }
        return String.join(",", brokers);
    }

    private String sanitize(String name) {
        return name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }
}
