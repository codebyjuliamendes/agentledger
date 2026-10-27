package io.github.codebyjuliamendes.agentledger.domain.policy;

import java.util.Optional;

/**
 * Extension point of the policy engine. Implementations must be pure and thread-safe: same input,
 * same result, no I/O.
 */
public interface PolicyRule {

  String id();

  Optional<RuleMatch> evaluate(ProposalContent content);
}
