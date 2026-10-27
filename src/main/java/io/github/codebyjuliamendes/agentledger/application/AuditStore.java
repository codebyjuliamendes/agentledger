package io.github.codebyjuliamendes.agentledger.application;

import io.github.codebyjuliamendes.agentledger.domain.audit.AuditEntry;
import io.github.codebyjuliamendes.agentledger.domain.audit.AuditHead;
import java.util.List;

/** Append-only persistence port for the audit ledger. No update or delete operations exist. */
public interface AuditStore {

  /** Reads the head and holds a write lock on it until the transaction ends. */
  AuditHead lockHead();

  AuditHead head();

  /** Inserts the entry and advances the head; caller must hold the head lock. */
  void append(AuditEntry entry);

  List<AuditEntry> forAction(long actionId);

  /** Entries with {@code seq > afterSeq}, ascending. */
  List<AuditEntry> page(long afterSeq, int limit);
}
