package io.github.codebyjuliamendes.agentledger.domain.policy;

import java.util.List;
import java.util.Optional;

/**
 * Configurable rule: fires when any term occurs as a substring of the proposal text. Substring
 * matching is intentionally over-inclusive ("overwrite" matches "write") because a false positive
 * only routes the proposal to a human.
 */
public record KeywordRule(String id, RiskLevel risk, List<String> terms) implements PolicyRule {

  public KeywordRule {
    terms = List.copyOf(terms);
  }

  @Override
  public Optional<RuleMatch> evaluate(ProposalContent content) {
    String text = content.searchableText();
    List<String> hits = terms.stream().filter(text::contains).toList();
    return hits.isEmpty() ? Optional.empty() : Optional.of(new RuleMatch(id, risk, hits));
  }
}
