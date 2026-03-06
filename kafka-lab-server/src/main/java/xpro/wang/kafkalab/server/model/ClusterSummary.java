package xpro.wang.kafkalab.server.model;

import java.util.List;

/**
 * Cluster overview snapshot.
 *
 * @param clusterId
 *            cluster identifier
 * @param controller
 *            controller broker id
 * @param brokers
 *            broker descriptions
 * @param brokerCount
 *            broker count
 */
public record ClusterSummary(String clusterId, String controller, List<String> brokers, int brokerCount) {
}
