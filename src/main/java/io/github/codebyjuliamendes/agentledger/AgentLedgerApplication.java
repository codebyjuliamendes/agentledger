package io.github.codebyjuliamendes.agentledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AgentLedgerApplication {

  public static void main(String[] args) {
    SpringApplication.run(AgentLedgerApplication.class, args);
  }
}
