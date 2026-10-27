package io.github.codebyjuliamendes.agentledger.web.dto;

import io.github.codebyjuliamendes.agentledger.domain.policy.DestructiveActionRule;
import io.github.codebyjuliamendes.agentledger.domain.policy.RiskPolicy;
import java.util.List;

/**
 * @param critical fixed, non-configurable terms of the mandatory rule (informational)
 */
public record PolicyResponse(List<String> medium, List<String> high, List<String> critical) {

  public static PolicyResponse from(RiskPolicy p) {
    return new PolicyResponse(p.mediumTerms(), p.highTerms(), DestructiveActionRule.BASE_TERMS);
  }
}
