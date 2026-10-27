package io.github.codebyjuliamendes.agentledger.domain.policy;

import java.util.List;

/** A rule that fired, the tier it assigns, and the terms that triggered it. */
public record RuleMatch(String ruleId, RiskLevel risk, List<String> matchedTerms) {

  public RuleMatch {
    matchedTerms = List.copyOf(matchedTerms);
  }
}
