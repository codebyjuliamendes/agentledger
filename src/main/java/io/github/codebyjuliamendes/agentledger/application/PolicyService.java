package io.github.codebyjuliamendes.agentledger.application;

import io.github.codebyjuliamendes.agentledger.domain.audit.AuditEventType;
import io.github.codebyjuliamendes.agentledger.domain.policy.RiskPolicy;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads and updates the workspace risk policy; every change is written to the ledger. */
@Service
public class PolicyService {

  private final PolicyStore store;
  private final AuditLedger ledger;

  public PolicyService(PolicyStore store, AuditLedger ledger) {
    this.store = store;
    this.ledger = ledger;
  }

  @Transactional(readOnly = true)
  public RiskPolicy current() {
    return store.load();
  }

  @Transactional
  public RiskPolicy update(List<String> medium, List<String> high, String actor) {
    RiskPolicy next = new RiskPolicy(medium, high);
    ledger.lockForWrite(); // concurrent updates would otherwise interleave delete/insert
    RiskPolicy previous = store.load();
    if (!previous.equals(next)) {
      store.save(next);
      ledger.append(
          null,
          AuditEventType.POLICY_UPDATED,
          actor,
          "Risk policy updated. Medium: [%s]; high: [%s]."
              .formatted(
                  String.join(", ", next.mediumTerms()), String.join(", ", next.highTerms())));
    }
    return next;
  }
}
