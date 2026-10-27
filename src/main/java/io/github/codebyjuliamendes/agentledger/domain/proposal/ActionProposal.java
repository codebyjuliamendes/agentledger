package io.github.codebyjuliamendes.agentledger.domain.proposal;

import io.github.codebyjuliamendes.agentledger.domain.policy.PolicyEvaluation;
import io.github.codebyjuliamendes.agentledger.domain.policy.ProposalContent;
import io.github.codebyjuliamendes.agentledger.domain.policy.RiskLevel;
import java.time.Instant;
import java.util.Objects;

/** An agent's proposed action with its policy verdict. Immutable; transitions return copies. */
public record ActionProposal(
    Long id,
    ProposalContent content,
    RiskLevel risk,
    ProposalStatus status,
    String policyRule,
    String policyDetail,
    Instant createdAt,
    Instant reviewedAt) {

  public ActionProposal {
    Objects.requireNonNull(content, "content");
    Objects.requireNonNull(risk, "risk");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(createdAt, "createdAt");
  }

  /** Creates an unsaved proposal whose initial status follows from the policy outcome. */
  public static ActionProposal submit(
      ProposalContent content, PolicyEvaluation evaluation, Instant now) {
    return new ActionProposal(
        null,
        content,
        evaluation.risk(),
        ProposalStatus.initialFor(evaluation.outcome()),
        evaluation.ruleId(),
        evaluation.explanation(),
        now,
        null);
  }

  public ActionProposal withId(long newId) {
    return new ActionProposal(
        newId, content, risk, status, policyRule, policyDetail, createdAt, reviewedAt);
  }

  /** Applies a human decision. Only pending proposals can be reviewed. */
  public ActionProposal review(ReviewDecision decision, Instant now) {
    ProposalStatus next = decision.resultingStatus();
    if (status == ProposalStatus.BLOCKED) {
      throw new ProposalException.Blocked();
    }
    if (!status.canTransitionTo(next)) {
      throw new ProposalException.InvalidTransition(
          status, next, "Only pending actions can be reviewed.");
    }
    return new ActionProposal(id, content, risk, next, policyRule, policyDetail, createdAt, now);
  }

  /** Records that an approved proposal was carried out by the agent. */
  public ActionProposal markExecuted() {
    if (!status.canTransitionTo(ProposalStatus.EXECUTED)) {
      throw new ProposalException.InvalidTransition(
          status, ProposalStatus.EXECUTED, "Only approved actions can be marked as executed.");
    }
    return new ActionProposal(
        id,
        content,
        risk,
        ProposalStatus.EXECUTED,
        policyRule,
        policyDetail,
        createdAt,
        reviewedAt);
  }
}
