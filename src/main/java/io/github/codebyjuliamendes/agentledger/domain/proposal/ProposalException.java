package io.github.codebyjuliamendes.agentledger.domain.proposal;

/** Domain failures of the proposal lifecycle; the web layer maps each to an HTTP status. */
public abstract sealed class ProposalException extends RuntimeException
    permits ProposalException.NotFound,
        ProposalException.Blocked,
        ProposalException.InvalidTransition {

  private ProposalException(String message) {
    super(message);
  }

  public static final class NotFound extends ProposalException {
    public NotFound(long id) {
      super("Action " + id + " not found.");
    }
  }

  /** A critical proposal was blocked by the mandatory rule and cannot be approved. */
  public static final class Blocked extends ProposalException {
    public Blocked() {
      super("Critical actions are blocked by a mandatory safety policy and cannot be approved.");
    }
  }

  public static final class InvalidTransition extends ProposalException {
    private final ProposalStatus from;
    private final ProposalStatus to;

    public InvalidTransition(ProposalStatus from, ProposalStatus to, String message) {
      super(message);
      this.from = from;
      this.to = to;
    }

    public ProposalStatus from() {
      return from;
    }

    public ProposalStatus to() {
      return to;
    }
  }
}
