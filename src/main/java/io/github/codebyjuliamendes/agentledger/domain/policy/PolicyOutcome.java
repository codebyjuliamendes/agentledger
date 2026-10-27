package io.github.codebyjuliamendes.agentledger.domain.policy;

/** Routing decided by the policy engine. */
public sealed interface PolicyOutcome {

  /** Low risk: recorded and executed (simulated) without a human. */
  record AutoApprove() implements PolicyOutcome {}

  /** Medium or high risk: waits for a human decision. */
  record RequireReview() implements PolicyOutcome {}

  /** Critical risk: blocked; the review API refuses approval. */
  record Block() implements PolicyOutcome {}

  static PolicyOutcome forRisk(RiskLevel risk) {
    return switch (risk) {
      case LOW -> new AutoApprove();
      case MEDIUM, HIGH -> new RequireReview();
      case CRITICAL -> new Block();
    };
  }
}
