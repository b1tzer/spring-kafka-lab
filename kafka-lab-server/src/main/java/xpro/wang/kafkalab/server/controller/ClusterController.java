package xpro.wang.kafkalab.server.controller;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xpro.wang.kafkalab.server.model.ApiResponse;
import xpro.wang.kafkalab.server.model.ClusterSummary;
import xpro.wang.kafkalab.server.service.ClusterAdminService;

/** REST endpoints for cluster-level read operations. */
@RestController
@RequestMapping
public class ClusterController {

  private final ClusterAdminService clusterAdminService;

  public ClusterController(ClusterAdminService clusterAdminService) {
    this.clusterAdminService = clusterAdminService;
  }

  /**
   * Returns cluster summary.
   *
   * @return cluster summary response
   * @throws Exception when cluster query fails
   */
  @GetMapping("/cluster")
  public ApiResponse<ClusterSummary> cluster() throws Exception {
    return ApiResponse.ok("Cluster fetched", clusterAdminService.clusterSummary());
  }

  /**
   * Returns broker list.
   *
   * @return broker response
   * @throws Exception when broker query fails
   */
  @GetMapping("/brokers")
  public ApiResponse<List<String>> brokers() throws Exception {
    return ApiResponse.ok("Brokers fetched", clusterAdminService.brokers());
  }

  /**
   * Returns dashboard metrics.
   *
   * @return dashboard response
   * @throws Exception when query fails
   */
  @GetMapping("/dashboard")
  public ApiResponse<Map<String, Object>> dashboard() throws Exception {
    return ApiResponse.ok("Dashboard data fetched", clusterAdminService.dashboard());
  }

  /**
   * Returns cluster topology visualization data.
   *
   * @return topology response
   * @throws Exception when query fails
   */
  @GetMapping("/cluster/topology")
  public ApiResponse<Map<String, Object>> topology() throws Exception {
    return ApiResponse.ok("Cluster topology fetched", clusterAdminService.topology());
  }
}
