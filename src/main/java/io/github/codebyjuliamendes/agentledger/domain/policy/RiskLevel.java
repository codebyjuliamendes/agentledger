package io.github.codebyjuliamendes.agentledger.domain.policy;

import java.util.Locale;

/** Risk tiers, ordered from least to most severe. */
public enum RiskLevel {
  LOW,
  MEDIUM,
  HIGH,
  CRITICAL;

  public boolean isAbove(RiskLevel other) {
    return compareTo(other) > 0;
  }

  /** Lower-case name used on the wire and in storage. */
  public String code() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static RiskLevel fromCode(String code) {
    return valueOf(code.toUpperCase(Locale.ROOT));
  }
}
