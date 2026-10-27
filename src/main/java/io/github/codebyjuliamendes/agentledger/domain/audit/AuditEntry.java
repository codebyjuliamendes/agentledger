package io.github.codebyjuliamendes.agentledger.domain.audit;

import java.time.Instant;
import java.util.Objects;

/**
 * One sealed ledger entry. {@code hash} covers every other field, including {@code prevHash}, so
 * altering, removing or reordering any entry breaks the chain from that point on.
 *
 * @param actionId the proposal this entry belongs to, or {@code null} for workspace events
 */
public record AuditEntry(
    long seq,
    Long actionId,
    String event,
    String actor,
    String detail,
    Instant createdAt,
    String prevHash,
    String hash) {

  public AuditEntry {
    Objects.requireNonNull(event, "event");
    Objects.requireNonNull(actor, "actor");
    Objects.requireNonNull(detail, "detail");
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(prevHash, "prevHash");
    Objects.requireNonNull(hash, "hash");
  }
}
