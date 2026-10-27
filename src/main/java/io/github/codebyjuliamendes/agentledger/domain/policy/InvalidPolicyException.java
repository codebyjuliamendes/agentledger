package io.github.codebyjuliamendes.agentledger.domain.policy;

/** Raised when a submitted risk policy violates its invariants. */
public class InvalidPolicyException extends RuntimeException {

  public InvalidPolicyException(String message) {
    super(message);
  }
}
