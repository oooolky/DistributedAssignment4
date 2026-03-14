package com.cs6650.databasenode;

import com.cs6650.common.config.NodeConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Application {
  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }

  @Bean
  public NodeConfig nodeConfig() {
    return NodeConfig.fromEnv();
  }
}
