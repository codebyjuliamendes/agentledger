package io.github.codebyjuliamendes.agentledger.web.dto;

import io.github.codebyjuliamendes.agentledger.application.ProposalStore.ProposalSummary;

/**
 * @param blocked proposals blocked by policy or rejected by a reviewer
 */
public record SummaryResponse(
    long total, long pending, long approved, long executed, long blocked) {

  public static SummaryResponse from(ProposalSummary s) {
    return new SummaryResponse(s.total(), s.pending(), s.approved(), s.executed(), s.blocked());
  }
}
