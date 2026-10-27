package io.github.codebyjuliamendes.agentledger.domain.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.codebyjuliamendes.agentledger.domain.policy.PolicyEngine;
import io.github.codebyjuliamendes.agentledger.domain.policy.ProposalContent;
import io.github.codebyjuliamendes.agentledger.domain.policy.RiskPolicy;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ActionProposalTest {

  private static final Instant NOW = Instant.parse("2026-10-27T12:00:00Z");

  private static ActionProposal submitted(String action) {
    ProposalContent content = new ProposalContent("agent", action, "res:1", "because");
    return ActionProposal.submit(
            content, PolicyEngine.forPolicy(RiskPolicy.DEFAULT).evaluate(content), NOW)
        .withId(7);
  }

  @Test
  void initialStatusFollowsRisk() {
    assertThat(submitted("Read profile").status()).isEqualTo(ProposalStatus.EXECUTED);
    assertThat(submitted("Refund payment").status()).isEqualTo(ProposalStatus.PENDING);
    assertThat(submitted("Export data").status()).isEqualTo(ProposalStatus.PENDING);
    assertThat(submitted("Drop table").status()).isEqualTo(ProposalStatus.BLOCKED);
  }

  @Test
  void approvalThenExecution() {
    ActionProposal approved =
        submitted("Refund payment").review(ReviewDecision.APPROVE, NOW.plusSeconds(5));

    assertThat(approved.status()).isEqualTo(ProposalStatus.APPROVED);
    assertThat(approved.reviewedAt()).isEqualTo(NOW.plusSeconds(5));
    assertThat(approved.markExecuted().status()).isEqualTo(ProposalStatus.EXECUTED);
  }

  @Test
  void denial() {
    assertThat(submitted("Refund payment").review(ReviewDecision.DENY, NOW).status())
        .isEqualTo(ProposalStatus.REJECTED);
  }

  @Test
  void blockedProposalCannotBeReviewed() {
    ActionProposal blocked = submitted("Delete production database");
    assertThatThrownBy(() -> blocked.review(ReviewDecision.APPROVE, NOW))
        .isInstanceOf(ProposalException.Blocked.class)
        .hasMessageContaining("cannot be approved");
  }

  @Test
  void onlyPendingProposalsCanBeReviewed() {
    ActionProposal rejected = submitted("Refund payment").review(ReviewDecision.DENY, NOW);
    assertThatThrownBy(() -> rejected.review(ReviewDecision.APPROVE, NOW))
        .isInstanceOfSatisfying(
            ProposalException.InvalidTransition.class,
            e -> {
              assertThat(e.from()).isEqualTo(ProposalStatus.REJECTED);
              assertThat(e.to()).isEqualTo(ProposalStatus.APPROVED);
            })
        .hasMessage("Only pending actions can be reviewed.");
  }

  @Test
  void onlyApprovedProposalsCanBeExecuted() {
    assertThatThrownBy(() -> submitted("Refund payment").markExecuted())
        .isInstanceOf(ProposalException.InvalidTransition.class)
        .hasMessage("Only approved actions can be marked as executed.");
  }

  @ParameterizedTest
  @CsvSource({
    "PENDING, APPROVED, true",
    "PENDING, REJECTED, true",
    "PENDING, EXECUTED, false",
    "PENDING, BLOCKED, false",
    "APPROVED, EXECUTED, true",
    "APPROVED, REJECTED, false",
    "REJECTED, APPROVED, false",
    "EXECUTED, PENDING, false",
    "BLOCKED, APPROVED, false",
  })
  void transitionTable(ProposalStatus from, ProposalStatus to, boolean allowed) {
    assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
  }

  @Test
  void terminalStatesAllowNoTransition() {
    for (ProposalStatus from : ProposalStatus.values()) {
      boolean anyAllowed = false;
      for (ProposalStatus to : ProposalStatus.values()) {
        anyAllowed |= from.canTransitionTo(to);
      }
      assertThat(anyAllowed).isEqualTo(!from.isTerminal());
      assertThat(ProposalStatus.fromCode(from.code())).isEqualTo(from);
    }
  }

  @Test
  void reviewDecisionCodes() {
    assertThat(ReviewDecision.fromCode("approve")).isEqualTo(ReviewDecision.APPROVE);
    assertThat(ReviewDecision.fromCode("deny").resultingStatus())
        .isEqualTo(ProposalStatus.REJECTED);
  }
}
