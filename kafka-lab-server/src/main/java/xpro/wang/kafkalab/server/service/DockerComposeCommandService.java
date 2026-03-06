package xpro.wang.kafkalab.server.service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Executes docker compose and docker CLI commands. */
@Service
public class DockerComposeCommandService {

    private final String workspaceRoot;
    private final String dockerConfig;

    public DockerComposeCommandService(@Value("${kafka-lab.env.workspace-root:${user.dir}}") String workspaceRoot,
            @Value("${kafka-lab.env.docker-config:}") String dockerConfig) {
        this.workspaceRoot = Paths.get(workspaceRoot).toAbsolutePath().normalize().toString();
        this.dockerConfig = dockerConfig == null ? "" : dockerConfig.trim();
    }

    public CommandResult runCompose(Path composeFile, String projectName, List<String> actionArgs) {
        Path composeDir = composeFile.getParent();
        if (composeDir == null) {
            return new CommandResult(false, "Compose directory not found: " + composeFile);
        }

        List<List<String>> candidates = composeCommandCandidates(composeDir);
        StringBuilder all = new StringBuilder();

        for (List<String> base : candidates) {
            List<String> command = new ArrayList<>(base);
            command.addAll(List.of("-p", projectName, "-f", composeFile.toString()));
            command.addAll(actionArgs);

            CommandResult result = execute(command, composeDir);
            all.append("$ ").append(String.join(" ", command)).append(System.lineSeparator());
            all.append(result.output()).append(System.lineSeparator());
            if (result.success()) {
                return new CommandResult(true, all.toString());
            }
        }

        return new CommandResult(false, all.toString());
    }

    public CommandResult runDockerCommand(Path workingDir, List<String> command) {
        return execute(command, workingDir);
    }

    private List<List<String>> composeCommandCandidates(Path composeDir) {
        List<List<String>> commands = new ArrayList<>();
        commands.add(List.of("docker", "compose"));
        commands.add(List.of("docker-compose"));

        List<String> dockerRun = new ArrayList<>();
        dockerRun.addAll(List.of("docker", "run", "--rm", "-v", "/var/run/docker.sock:/var/run/docker.sock", "-v",
                workspaceRoot + ":" + workspaceRoot, "-w", composeDir.toString(), "docker/compose:1.29.2"));
        commands.add(dockerRun);
        return commands;
    }

    private CommandResult execute(List<String> command, Path workingDir) {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workingDir.toFile());
        pb.redirectErrorStream(true);
        if (!dockerConfig.isBlank()) {
            pb.environment().put("DOCKER_CONFIG", dockerConfig);
        }

        try {
            Process process = pb.start();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            process.getInputStream().transferTo(output);
            boolean finished = process.waitFor(120, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new CommandResult(false, "Command timed out: " + String.join(" ", command));
            }
            String text = output.toString(StandardCharsets.UTF_8);
            return new CommandResult(process.exitValue() == 0, text);
        } catch (Exception ex) {
            return new CommandResult(false, ex.getMessage());
        }
    }

    public record CommandResult(boolean success, String output) {
    }
}
