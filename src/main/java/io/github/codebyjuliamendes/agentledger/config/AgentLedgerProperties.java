package io.github.codebyjuliamendes.agentledger.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Application settings under the {@code agentledger} prefix. */
@Validated
@ConfigurationProperties("agentledger")
public record AgentLedgerProperties(
    @DefaultValue("true") boolean seedDemoData,
    @Valid @DefaultValue Http http,
    @Valid @DefaultValue RateLimit rateLimit) {

  /**
   * @param maxBodyBytes largest accepted request body for API calls
   */
  public record Http(@DefaultValue("16384") @Min(256) long maxBodyBytes) {}

  /**
   * Token bucket applied per client address to API writes.
   *
   * @param capacity burst size
   * @param refillPerMinute tokens restored per minute
   * @param maxClients tracked addresses before idle buckets are evicted
   */
  public record RateLimit(
      @DefaultValue("true") boolean enabled,
      @DefaultValue("30") @Min(1) int capacity,
      @DefaultValue("30") @Min(1) int refillPerMinute,
      @DefaultValue("10000") @Min(1) int maxClients) {}
}
