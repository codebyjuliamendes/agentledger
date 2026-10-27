package io.github.codebyjuliamendes.agentledger.web;

import io.github.codebyjuliamendes.agentledger.application.AuditLedger;
import io.github.codebyjuliamendes.agentledger.web.dto.VerificationResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/audit", produces = MediaType.APPLICATION_JSON_VALUE)
class AuditController {

  private final AuditLedger ledger;

  AuditController(AuditLedger ledger) {
    this.ledger = ledger;
  }

  /** Recomputes every hash and link; a broken chain still returns 200 with {@code valid=false}. */
  @GetMapping("/verify")
  VerificationResponse verify() {
    return VerificationResponse.from(ledger.verify());
  }
}
