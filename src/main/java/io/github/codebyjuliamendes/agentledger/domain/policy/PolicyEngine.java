package io.github.codebyjuliamendes.agentledger.domain.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Evaluates proposals against an ordered list of rules; the most severe match wins. */
public final class PolicyEngine {

  private final List<PolicyRule> rules;

  public PolicyEngine(List<? extends PolicyRule> rules) {
    this.rules = List.copyOf(rules);
  }

  /** Standard rule set: the mandatory destructive rule followed by the configured terms. */
  public static PolicyEngine forPolicy(RiskPolicy policy) {
    return new PolicyEngine(
        List.of(
            new DestructiveActionRule(),
            new KeywordRule("configured-high", RiskLevel.HIGH, policy.highTerms()),
            new KeywordRule("configured-medium", RiskLevel.MEDIUM, policy.mediumTerms())));
  }

  public PolicyEvaluation evaluate(ProposalContent content) {
    List<RuleMatch> matches = new ArrayList<>();
    RuleMatch decisive = null;
    for (PolicyRule rule : rules) {
      Optional<RuleMatch> match = rule.evaluate(content);
      if (match.isEmpty()) {
        continue;
      }
      matches.add(match.get());
      if (decisive == null || match.get().risk().isAbove(decisive.risk())) {
        decisive = match.get();
      }
    }
    RiskLevel risk = decisive == null ? RiskLevel.LOW : decisive.risk();
    return new PolicyEvaluation(
        risk, PolicyOutcome.forRisk(risk), Optional.ofNullable(decisive), matches);
  }
}
