package xpro.wang.kafkalab.server.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/** Kafka related bean configuration. */
@Configuration
public class KafkaAdminConfig {

  /**
   * Creates a lazily initialized {@link AdminClient} bean.
   *
   * @param bootstrapServers bootstrap server list
   * @return admin client instance
   */
  @Bean
  @Lazy
  public AdminClient adminClient(
      @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
    Map<String, Object> configs = new HashMap<>();
    configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    return AdminClient.create(configs);
  }
}
