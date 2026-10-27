package io.github.codebyjuliamendes.agentledger.domain.audit;

import java.util.Optional;

/**
 * Incremental verifier: feed entries in {@code seq} order, then call {@link #result()}. Stateful
 * and not thread-safe; it lets callers verify arbitrarily long ledgers page by page.
 */
public final class ChainVerifier {

  private long expectedSeq = 1;
  private String expectedPrev = AuditChain.GENESIS_HASH;
  private long checked;
  private ChainVerification.Broken failure;

  /** Returns the failure if this entry broke the chain; later entries are ignored. */
  public Optional<ChainVerification.Broken> accept(AuditEntry entry) {
    if (failure != null) {
      return Optional.of(failure);
    }
    String reason = null;
    if (entry.seq() != expectedSeq) {
      reason = "Expected sequence " + expectedSeq + " but found " + entry.seq() + ".";
    } else if (!entry.prevHash().equals(expectedPrev)) {
      reason = "Previous-hash link does not match the preceding entry.";
    } else if (!AuditChain.hashOf(entry).equals(entry.hash())) {
      reason = "Entry content does not match its hash.";
    }
    if (reason != null) {
      failure = new ChainVerification.Broken(checked, entry.seq(), reason);
      return Optional.of(failure);
    }
    checked++;
    expectedSeq++;
    expectedPrev = entry.hash();
    return Optional.empty();
  }

  /**
   * Final verdict. {@code expectedHead} is the head recorded by the store; a mismatch reveals
   * truncation of the newest entries.
   */
  public ChainVerification result(long expectedHeadSeq, String expectedHeadHash) {
    if (failure != null) {
      return failure;
    }
    if (checked != expectedHeadSeq || !expectedPrev.equals(expectedHeadHash)) {
      return new ChainVerification.Broken(
          checked, checked + 1, "Ledger head does not match the last entry; entries are missing.");
    }
    return new ChainVerification.Intact(checked, expectedPrev);
  }
}
