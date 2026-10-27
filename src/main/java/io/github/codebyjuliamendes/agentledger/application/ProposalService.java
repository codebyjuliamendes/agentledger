package io.github.codebyjuliamendes.agentledger.application;

import io.github.codebyjuliamendes.agentledger.domain.audit.AuditEntry;
import io.github.codebyjuliamendes.agentledger.domain.audit.AuditEventType;
import io.github.codebyjuliamendes.agentledger.domain.policy.PolicyEngine;
import io.github.codebyjuliamendes.agentledger.domain.policy.PolicyEvaluation;
import io.github.codebyjuliamendes.agentledger.domain.policy.ProposalContent;
import io.github.codebyjuliamendes.agentledger.domain.proposal.ActionProposal;
import io.github.codebyjuliamendes.agentledger.domain.proposal.ProposalException;
import io.github.codebyjuliamendes.agentledger.domain.proposal.ProposalStatus;
import io.github.codebyjuliamendes.agentledger.domain.proposal.ReviewDecision;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Proposal use cases. Each state change and its ledger entries commit in one transaction. */
@Service
public class ProposalService {

  static final String DEFAULT_REVIEWER = "workspace owner";
  static final String POLICY_ACTOR = "policy engine";

  private static final Logger log = LoggerFactory.getLogger(ProposalService.class);

  private final ProposalStore proposals;
  private final PolicyStore policies;
  private final AuditLedger ledger;
  private final Clock clock;

  public ProposalService(
      ProposalStore proposals, PolicyStore policies, AuditLedger ledger, Clock clock) {
    this.proposals = proposals;
    this.policies = policies;
    this.ledger = ledger;
    this.clock = clock;
  }

  @Transactional
  public ActionProposal submit(ProposalContent content) {
    PolicyEvaluation evaluation = PolicyEngine.forPolicy(policies.load()).evaluate(content);
    ActionProposal proposal = proposals.insert(ActionProposal.submit(content, evaluation, now()));
    ledger.append(
        proposal.id(),
        AuditEventType.PROPOSED,
        content.agent(),
        "Agent submitted action proposal.");
    switch (proposal.status()) {
      case BLOCKED ->
          ledger.append(
              proposal.id(),
              AuditEventType.BLOCKED,
              POLICY_ACTOR,
              "Critical action blocked; human review required. " + evaluation.explanation());
      case EXECUTED ->
          ledger.append(
              proposal.id(),
              AuditEventType.EXECUTED,
              POLICY_ACTOR,
              "Low-risk action approved and simulated. No external system was changed.");
      case PENDING -> { // waits for a reviewer; the proposed entry is enough
      }
      case APPROVED, REJECTED ->
          throw new IllegalStateException("Unexpected initial status " + proposal.status());
    }
    log.info(
        "Proposal {} recorded: risk={} status={} rule={}",
        proposal.id(),
        proposal.risk().code(),
        proposal.status().code(),
        proposal.policyRule());
    return proposal;
  }

  @Transactional
  public ActionProposal review(long id, ReviewDecision decision, String reviewer, String note) {
    ActionProposal current = get(id);
    ActionProposal reviewed = current.review(decision, now());
    saveTransition(reviewed, current.status());
    String actor = blankToDefault(reviewer, DEFAULT_REVIEWER);
    String verb = decision == ReviewDecision.APPROVE ? "approved" : "rejected";
    ledger.append(
        id,
        AuditEventType.forStatus(reviewed.status()),
        actor,
        blankToDefault(
            note,
            "Human reviewer "
                + verb
                + " the proposed action. This demo does not execute external"
                + " operations."));
    log.info("Proposal {} {} by reviewer", id, verb);
    return reviewed;
  }

  /** Agent reports that an approved action was carried out. */
  @Transactional
  public ActionProposal markExecuted(long id, String actor, String note) {
    ActionProposal current = get(id);
    ActionProposal executed = current.markExecuted();
    saveTransition(executed, current.status());
    ledger.append(
        id,
        AuditEventType.EXECUTED,
        blankToDefault(actor, current.content().agent()),
        blankToDefault(note, "Agent reported the approved action as executed."));
    return executed;
  }

  @Transactional(readOnly = true)
  public ActionProposal get(long id) {
    return proposals.findById(id).orElseThrow(() -> new ProposalException.NotFound(id));
  }

  @Transactional(readOnly = true)
  public List<ActionProposal> list(Optional<ProposalStatus> status, int limit) {
    return proposals.list(status, limit);
  }

  @Transactional(readOnly = true)
  public ProposalStore.ProposalSummary summary() {
    return proposals.summary();
  }

  @Transactional(readOnly = true)
  public List<AuditEntry> trail(long id) {
    get(id);
    return ledger.forAction(id);
  }

  private void saveTransition(ActionProposal updated, ProposalStatus expected) {
    if (!proposals.updateStatus(updated, expected)) {
      throw new ProposalException.InvalidTransition(
          expected,
          updated.status(),
          "The action was changed by another request. Reload and try again.");
    }
  }

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }

  private static String blankToDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.strip();
  }
}
