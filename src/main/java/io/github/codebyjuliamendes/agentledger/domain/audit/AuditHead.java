package io.github.codebyjuliamendes.agentledger.domain.audit;

/** Latest entry of the ledger as recorded by the store. */
public record AuditHead(long seq, String hash) {

  public static final AuditHead EMPTY = new AuditHead(0, AuditChain.GENESIS_HASH);
}
