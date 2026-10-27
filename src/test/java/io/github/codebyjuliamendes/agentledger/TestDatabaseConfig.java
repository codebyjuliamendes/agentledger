package io.github.codebyjuliamendes.agentledger;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Every test context starts from an empty schema. H2 databases are already unique per context; this
 * matters when the suite runs against a shared PostgreSQL (see CI).
 */
@Configuration(proxyBeanMethods = false)
@Profile("test")
class TestDatabaseConfig {

  @Bean
  FlywayMigrationStrategy cleanMigrate() {
    return flyway -> {
      flyway.clean();
      flyway.migrate();
    };
  }
}
