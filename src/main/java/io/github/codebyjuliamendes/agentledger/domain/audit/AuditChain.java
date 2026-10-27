package io.github.codebyjuliamendes.agentledger.domain.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

/** SHA-256 hash chain over audit entries. */
public final class AuditChain {

  /** Predecessor hash of the first entry. */
  public static final String GENESIS_HASH = "0".repeat(64);

  private AuditChain() {}

  /** Appends a new entry after {@code (headSeq, headHash)}. */
  public static AuditEntry seal(
      long headSeq,
      String headHash,
      Long actionId,
      AuditEventType type,
      String actor,
      String detail,
      Instant createdAt) {
    long seq = headSeq + 1;
    // Storage keeps microseconds; truncate so the hash is reproducible after a round trip.
    Instant at = createdAt.truncatedTo(ChronoUnit.MICROS);
    String hash = hash(seq, actionId, type.code(), actor, detail, at, headHash);
    return new AuditEntry(seq, actionId, type.code(), actor, detail, at, headHash, hash);
  }

  public static String hashOf(AuditEntry e) {
    return hash(
        e.seq(), e.actionId(), e.event(), e.actor(), e.detail(), e.createdAt(), e.prevHash());
  }

  /**
   * Hashes a length-prefixed canonical encoding so that field boundaries are unambiguous (e.g.
   * actor "a|b" + detail "c" cannot collide with actor "a" + detail "b|c").
   */
  static String hash(
      long seq,
      Long actionId,
      String event,
      String actor,
      String detail,
      Instant createdAt,
      String prevHash) {
    StringBuilder canonical = new StringBuilder(256).append("agentledger/v1\n");
    for (String field :
        new String[] {
          Long.toString(seq),
          actionId == null ? null : Long.toString(actionId),
          event,
          actor,
          detail,
          createdAt.truncatedTo(ChronoUnit.MICROS).toString(),
          prevHash
        }) {
      if (field == null) {
        canonical.append("-1:\n");
      } else {
        canonical.append(field.length()).append(':').append(field).append('\n');
      }
    }
    return HexFormat.of()
        .formatHex(sha256().digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required by the Java platform", e);
    }
  }
}
