package io.github.codebyjuliamendes.agentledger.domain.policy;

import java.util.List;
import java.util.Optional;

/**
 * Result of evaluating one proposal.
 *
 * @param decisive the highest-risk match, empty when no rule fired
 * @param matches every rule that fired, in rule order
 */
public record PolicyEvaluation(
    RiskLevel risk, PolicyOutcome outcome, Optional<RuleMatch> decisive, List<RuleMatch> matches) {

  public static final String DEFAULT_RULE_ID = "default-low";

  public PolicyEvaluation {
    matches = List.copyOf(matches);
  }

  public String ruleId() {
    return decisive.map(RuleMatch::ruleId).orElse(DEFAULT_RULE_ID);
  }

  /** Human-readable explanation stored with the proposal. */
  public String explanation() {
    return decisive
        .map(
            m ->
                "Rule %s classified the action as %s (matched: %s)."
                    .formatted(m.ruleId(), m.risk().code(), String.join(", ", m.matchedTerms())))
        .orElse("No risk rule matched; the action is low risk.");
  }
}
