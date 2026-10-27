package io.github.codebyjuliamendes.agentledger.domain.audit;

/** Outcome of verifying the ledger. */
public sealed interface ChainVerification {

  long checked();

  record Intact(long checked, String headHash) implements ChainVerification {}

  record Broken(long checked, long seq, String reason) implements ChainVerification {}
}
