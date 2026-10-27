package io.github.codebyjuliamendes.agentledger.application;

import io.github.codebyjuliamendes.agentledger.domain.audit.AuditChain;
import io.github.codebyjuliamendes.agentledger.domain.audit.AuditEntry;
import io.github.codebyjuliamendes.agentledger.domain.audit.AuditEventType;
import io.github.codebyjuliamendes.agentledger.domain.audit.AuditHead;
import io.github.codebyjuliamendes.agentledger.domain.audit.ChainVerification;
import io.github.codebyjuliamendes.agentledger.domain.audit.ChainVerifier;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Appends hash-chained entries and verifies the chain. */
@Service
public class AuditLedger {

  static final int VERIFY_PAGE_SIZE = 500;

  private final AuditStore store;
  private final Clock clock;

  public AuditLedger(AuditStore store, Clock clock) {
    this.store = store;
    this.clock = clock;
  }

  /**
   * Appends within the caller's transaction so the entry commits atomically with the state change
   * it describes. The head lock serializes concurrent appends.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public AuditEntry append(Long actionId, AuditEventType type, String actor, String detail) {
    AuditHead head = store.lockHead();
    AuditEntry entry =
        AuditChain.seal(head.seq(), head.hash(), actionId, type, actor, detail, clock.instant());
    store.append(entry);
    return entry;
  }

  /**
   * Takes the ledger write lock early, for use cases that must read-modify-write other state before
   * appending (serializes them with every other ledger writer).
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void lockForWrite() {
    store.lockHead();
  }

  @Transactional(readOnly = true)
  public List<AuditEntry> forAction(long actionId) {
    return store.forAction(actionId);
  }

  @Transactional(readOnly = true)
  public ChainVerification verify() {
    AuditHead head = store.head();
    ChainVerifier verifier = new ChainVerifier();
    long after = 0;
    while (after < head.seq()) {
      List<AuditEntry> page = store.page(after, VERIFY_PAGE_SIZE);
      if (page.isEmpty()) {
        break;
      }
      for (AuditEntry entry : page) {
        if (entry.seq() > head.seq()) {
          break;
        }
        if (verifier.accept(entry).isPresent()) {
          return verifier.result(head.seq(), head.hash());
        }
      }
      after = page.getLast().seq();
    }
    return verifier.result(head.seq(), head.hash());
  }
}
