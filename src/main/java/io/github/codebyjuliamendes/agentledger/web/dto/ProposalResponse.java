package io.github.codebyjuliamendes.agentledger.web.dto;

import io.github.codebyjuliamendes.agentledger.domain.proposal.ActionProposal;
import java.time.Instant;

/** Wire format of a proposal (snake_case via the global Jackson naming strategy). */
public record ProposalResponse(
    long id,
    String agent,
    String action,
    String target,
    String reason,
    String risk,
    String status,
    String policyRule,
    String policyDetail,
    Instant createdAt,
    Instant reviewedAt) {

  public static ProposalResponse from(ActionProposal p) {
    return new ProposalResponse(
        p.id(),
        p.content().agent(),
        p.content().action(),
        p.content().target(),
        p.content().reason(),
        p.risk().code(),
        p.status().code(),
        p.policyRule(),
        p.policyDetail(),
        p.createdAt(),
        p.reviewedAt());
  }
}
