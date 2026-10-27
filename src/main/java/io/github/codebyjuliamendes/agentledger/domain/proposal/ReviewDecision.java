package io.github.codebyjuliamendes.agentledger.domain.proposal;

import java.util.Locale;

/** A human reviewer's verdict on a pending proposal. */
public enum ReviewDecision {
  APPROVE(ProposalStatus.APPROVED),
  DENY(ProposalStatus.REJECTED);

  private final ProposalStatus resultingStatus;

  ReviewDecision(ProposalStatus resultingStatus) {
    this.resultingStatus = resultingStatus;
  }

  public ProposalStatus resultingStatus() {
    return resultingStatus;
  }

  public static ReviewDecision fromCode(String code) {
    return valueOf(code.toUpperCase(Locale.ROOT));
  }
}
