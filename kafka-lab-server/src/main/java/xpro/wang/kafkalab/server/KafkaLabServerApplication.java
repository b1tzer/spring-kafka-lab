package xpro.wang.kafkalab.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Kafka Lab backend service bootstrap class. */
@SpringBootApplication
public class KafkaLabServerApplication {

  /**
   * Application entry point.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(KafkaLabServerApplication.class, args);
  }
}
