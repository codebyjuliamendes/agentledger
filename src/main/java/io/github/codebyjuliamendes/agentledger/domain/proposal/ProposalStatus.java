package io.github.codebyjuliamendes.agentledger.domain.proposal;

import io.github.codebyjuliamendes.agentledger.domain.policy.PolicyOutcome;
import java.util.Locale;

/**
 * Proposal lifecycle. Allowed transitions:
 *
 * <pre>
 *   PENDING  -> APPROVED | REJECTED
 *   APPROVED -> EXECUTED
 *   EXECUTED, REJECTED, BLOCKED are terminal
 * </pre>
 */
public enum ProposalStatus {
  PENDING,
  APPROVED,
  REJECTED,
  EXECUTED,
  BLOCKED;

  public static ProposalStatus initialFor(PolicyOutcome outcome) {
    return switch (outcome) {
      case PolicyOutcome.AutoApprove a -> EXECUTED;
      case PolicyOutcome.RequireReview r -> PENDING;
      case PolicyOutcome.Block b -> BLOCKED;
    };
  }

  public boolean canTransitionTo(ProposalStatus next) {
    return switch (this) {
      case PENDING -> next == APPROVED || next == REJECTED;
      case APPROVED -> next == EXECUTED;
      case REJECTED, EXECUTED, BLOCKED -> false;
    };
  }

  public boolean isTerminal() {
    return switch (this) {
      case PENDING, APPROVED -> false;
      case REJECTED, EXECUTED, BLOCKED -> true;
    };
  }

  public String code() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static ProposalStatus fromCode(String code) {
    return valueOf(code.toUpperCase(Locale.ROOT));
  }
}
