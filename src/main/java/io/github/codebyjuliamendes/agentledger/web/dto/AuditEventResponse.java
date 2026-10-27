package io.github.codebyjuliamendes.agentledger.web.dto;

import io.github.codebyjuliamendes.agentledger.domain.audit.AuditEntry;
import java.time.Instant;

public record AuditEventResponse(
    long id,
    Long actionId,
    String event,
    String actor,
    String detail,
    Instant createdAt,
    String prevHash,
    String hash) {

  public static AuditEventResponse from(AuditEntry e) {
    return new AuditEventResponse(
        e.seq(),
        e.actionId(),
        e.event(),
        e.actor(),
        e.detail(),
        e.createdAt(),
        e.prevHash(),
        e.hash());
  }
}
