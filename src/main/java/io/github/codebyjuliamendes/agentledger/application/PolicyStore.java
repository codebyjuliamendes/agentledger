package io.github.codebyjuliamendes.agentledger.application;

import io.github.codebyjuliamendes.agentledger.domain.policy.RiskPolicy;

/** Persistence port for the workspace risk policy. */
public interface PolicyStore {

  RiskPolicy load();

  void save(RiskPolicy policy);
}
