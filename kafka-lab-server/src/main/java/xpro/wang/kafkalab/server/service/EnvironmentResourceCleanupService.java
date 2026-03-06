package xpro.wang.kafkalab.server.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import xpro.wang.kafkalab.server.model.EnvironmentInstance;

/**
 * Cleans docker resources for an environment instance.
 *
 * <p>Strategy: 1) Run docker compose down -v --remove-orphans. 2) Fallback cleanup by compose
 * project label to handle future service expansion. 3) Optionally delete runtime directory.
 */
@Service
public class EnvironmentResourceCleanupService {

  private final DockerComposeCommandService dockerComposeCommandService;

  public EnvironmentResourceCleanupService(
      DockerComposeCommandService dockerComposeCommandService) {
    this.dockerComposeCommandService = dockerComposeCommandService;
  }

  public CleanupResult cleanup(EnvironmentInstance instance, boolean deleteRuntimeDirectory) {
    StringBuilder output = new StringBuilder();

    Path composeFile = Path.of(instance.composeFilePath());
    Path composeDir = composeFile.getParent();

    if (composeDir == null) {
      return new CleanupResult(false, "Compose directory not found: " + composeFile);
    }

    if (Files.exists(composeFile)) {
      DockerComposeCommandService.CommandResult composeDown =
          dockerComposeCommandService.runCompose(
              composeFile, instance.projectName(), List.of("down", "-v", "--remove-orphans"));
      append(output, "compose-down", composeDown.output());
    } else {
      append(
          output, "compose-down", "compose file missing, skip compose-down and run label cleanup");
    }

    CleanupStepResult containers = cleanupContainersByLabel(composeDir, instance.projectName());
    append(output, "containers", containers.output());

    CleanupStepResult volumes = cleanupVolumesByLabel(composeDir, instance.projectName());
    append(output, "volumes", volumes.output());

    CleanupStepResult networks = cleanupNetworksByLabel(composeDir, instance.projectName());
    append(output, "networks", networks.output());

    boolean dirDeleted = true;
    if (deleteRuntimeDirectory) {
      try {
        deleteDirectory(composeDir);
        append(output, "runtime-dir", "deleted: " + composeDir);
      } catch (IOException ex) {
        dirDeleted = false;
        append(output, "runtime-dir", "delete failed: " + ex.getMessage());
      }
    }

    boolean success = containers.success() && volumes.success() && networks.success() && dirDeleted;
    return new CleanupResult(success, output.toString());
  }

  private CleanupStepResult cleanupContainersByLabel(Path workingDir, String projectName) {
    DockerComposeCommandService.CommandResult list =
        dockerComposeCommandService.runDockerCommand(
            workingDir,
            List.of(
                "docker",
                "ps",
                "-aq",
                "--filter",
                "label=com.docker.compose.project=" + projectName));
    if (!list.success()) {
      return new CleanupStepResult(false, list.output());
    }

    List<String> ids = parseIds(list.output());
    if (ids.isEmpty()) {
      return new CleanupStepResult(true, "no containers");
    }

    List<String> removeCmd = new ArrayList<>();
    removeCmd.add("docker");
    removeCmd.add("rm");
    removeCmd.add("-f");
    removeCmd.addAll(ids);
    DockerComposeCommandService.CommandResult remove =
        dockerComposeCommandService.runDockerCommand(workingDir, removeCmd);
    return new CleanupStepResult(remove.success(), remove.output());
  }

  private CleanupStepResult cleanupVolumesByLabel(Path workingDir, String projectName) {
    DockerComposeCommandService.CommandResult list =
        dockerComposeCommandService.runDockerCommand(
            workingDir,
            List.of(
                "docker",
                "volume",
                "ls",
                "-q",
                "--filter",
                "label=com.docker.compose.project=" + projectName));
    if (!list.success()) {
      return new CleanupStepResult(false, list.output());
    }

    List<String> ids = parseIds(list.output());
    if (ids.isEmpty()) {
      return new CleanupStepResult(true, "no volumes");
    }

    List<String> removeCmd = new ArrayList<>();
    removeCmd.add("docker");
    removeCmd.add("volume");
    removeCmd.add("rm");
    removeCmd.addAll(ids);
    DockerComposeCommandService.CommandResult remove =
        dockerComposeCommandService.runDockerCommand(workingDir, removeCmd);
    return new CleanupStepResult(remove.success(), remove.output());
  }

  private CleanupStepResult cleanupNetworksByLabel(Path workingDir, String projectName) {
    DockerComposeCommandService.CommandResult list =
        dockerComposeCommandService.runDockerCommand(
            workingDir,
            List.of(
                "docker",
                "network",
                "ls",
                "-q",
                "--filter",
                "label=com.docker.compose.project=" + projectName));
    if (!list.success()) {
      return new CleanupStepResult(false, list.output());
    }

    List<String> ids = parseIds(list.output());
    if (ids.isEmpty()) {
      return new CleanupStepResult(true, "no networks");
    }

    boolean success = true;
    StringBuilder all = new StringBuilder();
    for (String id : ids) {
      DockerComposeCommandService.CommandResult remove =
          dockerComposeCommandService.runDockerCommand(
              workingDir, List.of("docker", "network", "rm", id));
      all.append(remove.output()).append(System.lineSeparator());
      if (!remove.success()) {
        success = false;
      }
    }
    return new CleanupStepResult(success, all.toString());
  }

  private List<String> parseIds(String raw) {
    return raw == null
        ? List.of()
        : raw.lines().map(String::trim).filter(line -> !line.isBlank()).distinct().toList();
  }

  private void append(StringBuilder sb, String step, String text) {
    sb.append("[").append(step).append("]").append(System.lineSeparator());
    sb.append(text == null ? "" : text).append(System.lineSeparator());
  }

  private void deleteDirectory(Path directory) throws IOException {
    if (directory == null || !Files.exists(directory)) {
      return;
    }
    try (var walk = Files.walk(directory)) {
      walk.sorted(Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.deleteIfExists(path);
                } catch (IOException ex) {
                  throw new IllegalStateException("Failed to delete " + path, ex);
                }
              });
    }
  }

  private record CleanupStepResult(boolean success, String output) {}

  public record CleanupResult(boolean success, String output) {}
}
