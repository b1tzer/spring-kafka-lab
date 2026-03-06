package xpro.wang.kafkalab.server.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.common.Node;
import org.springframework.stereotype.Service;
import xpro.wang.kafkalab.server.model.ClusterSummary;

/** Cluster administration query service. */
@Service
public class ClusterAdminService {

  private final RuntimeKafkaConnectionService runtimeKafkaConnectionService;
  private final TopicAdminService topicAdminService;
  private final ProducerLabService producerLabService;
  private final ConsumerLabService consumerLabService;

  public ClusterAdminService(
      RuntimeKafkaConnectionService runtimeKafkaConnectionService,
      TopicAdminService topicAdminService,
      ProducerLabService producerLabService,
      ConsumerLabService consumerLabService) {
    this.runtimeKafkaConnectionService = runtimeKafkaConnectionService;
    this.topicAdminService = topicAdminService;
    this.producerLabService = producerLabService;
    this.consumerLabService = consumerLabService;
  }

  /**
   * Returns cluster summary details.
   *
   * @return cluster summary
   * @throws Exception when query fails
   */
  public ClusterSummary clusterSummary() throws Exception {
    try (AdminClient adminClient = adminClient()) {
      var describeCluster = adminClient.describeCluster();
      Collection<Node> nodes = describeCluster.nodes().get();
      String controller =
          describeCluster.controller().get() == null
              ? "N/A"
              : describeCluster.controller().get().idString();

      List<String> brokers =
          nodes.stream()
              .map(node -> node.idString() + "@" + node.host() + ":" + node.port())
              .toList();

      return new ClusterSummary(
          describeCluster.clusterId().get(), controller, brokers, brokers.size());
    }
  }

  /**
   * Returns broker list.
   *
   * @return broker descriptions
   * @throws Exception when query fails
   */
  public List<String> brokers() throws Exception {
    try (AdminClient adminClient = adminClient()) {
      return adminClient.describeCluster().nodes().get().stream()
          .map(node -> node.idString() + "@" + node.host() + ":" + node.port())
          .toList();
    }
  }

  /**
   * Returns dashboard overview metrics.
   *
   * @return dashboard metrics map
   * @throws Exception when query fails
   */
  public Map<String, Object> dashboard() throws Exception {
    var topics = topicAdminService.listTopics();
    try (AdminClient adminClient = adminClient()) {
      var groups = adminClient.listConsumerGroups().all().get();
      return Map.of(
          "brokerCount", brokers().size(),
          "topicCount", topics.size(),
          "consumerGroupCount", groups.size());
    }
  }

  /** Returns topology data for realtime visualization. */
  public Map<String, Object> topology() throws Exception {
    try (AdminClient adminClient = adminClient()) {
      var describe = adminClient.describeCluster();
      Collection<Node> nodes = describe.nodes().get();
      Node controller = describe.controller().get();
      Integer controllerId = controller == null ? null : controller.id();

      List<Map<String, Object>> brokers =
          nodes.stream()
              .map(
                  node ->
                      Map.of(
                          "id", node.id(),
                          "host", node.host(),
                          "port", node.port(),
                          "rack", node.rack() == null ? "N/A" : node.rack(),
                          "status", "UP",
                          "roles", List.of("broker", "controller-eligible"),
                          "activeController", controllerId != null && controllerId == node.id()))
              .toList();

      List<Map<String, Object>> topicItems =
          topicAdminService.listTopics().stream()
              .sorted()
              .map(
                  topicName -> {
                    try {
                      Map<String, Object> detail = topicAdminService.describeTopic(topicName);
                      @SuppressWarnings("unchecked")
                      List<Map<String, Object>> partitions =
                          (List<Map<String, Object>>) detail.getOrDefault("partitions", List.of());

                      List<Map<String, Object>> enhancedPartitions =
                          partitions.stream()
                              .map(
                                  partition -> {
                                    int partitionId = (int) partition.get("partition");
                                    List<Map<String, Object>> recentMessages =
                                        producerLabService.recentMessages(
                                            topicName, partitionId, 10);
                                    return Map.of(
                                        "partition",
                                        partitionId,
                                        "leader",
                                        partition.getOrDefault("leader", "N/A"),
                                        "replicas",
                                        partition.getOrDefault("replicas", List.of()),
                                        "recentMessages",
                                        recentMessages);
                                  })
                              .toList();

                      return Map.of(
                          "topic", topicName,
                          "partitionCount", detail.getOrDefault("partitionCount", 0),
                          "partitions", enhancedPartitions);
                    } catch (Exception ex) {
                      return Map.of(
                          "topic",
                          topicName,
                          "partitionCount",
                          0,
                          "partitions",
                          List.of(),
                          "error",
                          ex.getMessage());
                    }
                  })
              .toList();

      List<Map<String, Object>> activeProducers = producerLabService.activeProducers();
      List<Map<String, Object>> activeConsumers = consumerLabService.localConsumers();

      return Map.of(
          "clusterId", describe.clusterId().get(),
          "kafkaMode", "KRaft",
          "brokerCount", brokers.size(),
          "brokers", brokers,
          "producer", Map.of("activeCount", activeProducers.size(), "active", activeProducers),
          "consumer",
              Map.of("activeCount", activeConsumers.size(), "subscriptions", activeConsumers),
          "topics", topicItems);
    }
  }

  private AdminClient adminClient() {
    return AdminClient.create(
        Map.of(
            AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
            runtimeKafkaConnectionService.bootstrapServers()));
  }
}
