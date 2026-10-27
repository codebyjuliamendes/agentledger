package io.github.codebyjuliamendes.agentledger.domain.audit;

import io.github.codebyjuliamendes.agentledger.domain.proposal.ProposalStatus;
import java.util.Locale;

/** Kinds of ledger entries. */
public enum AuditEventType {
  PROPOSED,
  APPROVED,
  REJECTED,
  EXECUTED,
  BLOCKED,
  POLICY_UPDATED;

  public static AuditEventType forStatus(ProposalStatus status) {
    return switch (status) {
      case APPROVED -> APPROVED;
      case REJECTED -> REJECTED;
      case EXECUTED -> EXECUTED;
      case BLOCKED -> BLOCKED;
      case PENDING -> PROPOSED;
    };
  }

  public String code() {
    return name().toLowerCase(Locale.ROOT);
  }
}
