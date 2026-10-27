package io.github.codebyjuliamendes.agentledger.application;

import io.github.codebyjuliamendes.agentledger.domain.proposal.ActionProposal;
import io.github.codebyjuliamendes.agentledger.domain.proposal.ProposalStatus;
import java.util.List;
import java.util.Optional;

/** Persistence port for proposals. */
public interface ProposalStore {

  ActionProposal insert(ActionProposal proposal);

  Optional<ActionProposal> findById(long id);

  /**
   * Compare-and-set status update: succeeds only if the stored status is still {@code expected}.
   *
   * @return false when a concurrent writer changed the proposal first
   */
  boolean updateStatus(ActionProposal updated, ProposalStatus expected);

  List<ActionProposal> list(Optional<ProposalStatus> status, int limit);

  ProposalSummary summary();

  long count();

  record ProposalSummary(long total, long pending, long approved, long executed, long blocked) {}
}
