package io.github.codebyjuliamendes.agentledger.domain.policy;

import java.util.Locale;
import java.util.Objects;

/** The agent-supplied fields of a proposal. Values are trimmed on construction. */
public record ProposalContent(String agent, String action, String target, String reason) {

  public ProposalContent {
    agent = Objects.requireNonNull(agent, "agent").strip();
    action = Objects.requireNonNull(action, "action").strip();
    target = Objects.requireNonNull(target, "target").strip();
    reason = Objects.requireNonNull(reason, "reason").strip();
  }

  /** Text inspected by policy rules. The agent name is deliberately excluded. */
  public String searchableText() {
    return (action + " " + target + " " + reason).toLowerCase(Locale.ROOT);
  }
}
