package io.github.codebyjuliamendes.agentledger.domain.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuditChainTest {

  private static final Instant T0 = Instant.parse("2026-10-27T12:00:00.123456789Z");

  private static List<AuditEntry> chain(int n) {
    List<AuditEntry> entries = new ArrayList<>();
    AuditHead head = AuditHead.EMPTY;
    for (int i = 0; i < n; i++) {
      AuditEntry e =
          AuditChain.seal(
              head.seq(),
              head.hash(),
              i % 3 == 0 ? null : (long) i,
              AuditEventType.PROPOSED,
              "actor " + i,
              "detail " + i,
              T0.plusSeconds(i));
      entries.add(e);
      head = new AuditHead(e.seq(), e.hash());
    }
    return entries;
  }

  private static ChainVerification verify(List<AuditEntry> entries, long headSeq, String headHash) {
    ChainVerifier verifier = new ChainVerifier();
    entries.forEach(verifier::accept);
    return verifier.result(headSeq, headHash);
  }

  private static ChainVerification verify(List<AuditEntry> entries) {
    AuditEntry last = entries.getLast();
    return verify(entries, last.seq(), last.hash());
  }

  @Test
  void sealsSequentialLinkedEntries() {
    List<AuditEntry> entries = chain(3);

    assertThat(entries.getFirst().seq()).isEqualTo(1);
    assertThat(entries.getFirst().prevHash()).isEqualTo(AuditChain.GENESIS_HASH);
    assertThat(entries.get(1).prevHash()).isEqualTo(entries.get(0).hash());
    assertThat(entries).allSatisfy(e -> assertThat(e.hash()).hasSize(64).matches("[0-9a-f]+"));
    assertThat(entries.getFirst().createdAt()).isEqualTo("2026-10-27T12:00:00.123456Z");
  }

  @Test
  void hashIsDeterministic() {
    assertThat(chain(4)).isEqualTo(chain(4));
  }

  @Test
  void intactChainVerifies() {
    List<AuditEntry> entries = chain(5);
    assertThat(verify(entries))
        .isEqualTo(new ChainVerification.Intact(5, entries.getLast().hash()));
  }

  @Test
  void emptyLedgerIsIntact() {
    assertThat(verify(List.of(), 0, AuditChain.GENESIS_HASH))
        .isEqualTo(new ChainVerification.Intact(0, AuditChain.GENESIS_HASH));
  }

  @Test
  void detectsEditedContent() {
    List<AuditEntry> entries = new ArrayList<>(chain(5));
    AuditEntry e = entries.get(2);
    entries.set(
        2,
        new AuditEntry(
            e.seq(),
            e.actionId(),
            "approved",
            e.actor(),
            e.detail(),
            e.createdAt(),
            e.prevHash(),
            e.hash()));

    assertThat(verify(entries))
        .isInstanceOfSatisfying(
            ChainVerification.Broken.class,
            b -> {
              assertThat(b.seq()).isEqualTo(3);
              assertThat(b.checked()).isEqualTo(2);
              assertThat(b.reason()).contains("hash");
            });
  }

  @Test
  void detectsRecomputedHashWithoutRelinking() {
    List<AuditEntry> entries = new ArrayList<>(chain(4));
    AuditEntry e = entries.get(1);
    AuditEntry forged =
        new AuditEntry(
            e.seq(),
            e.actionId(),
            e.event(),
            "mallory",
            e.detail(),
            e.createdAt(),
            e.prevHash(),
            "");
    entries.set(
        1,
        new AuditEntry(
            e.seq(),
            e.actionId(),
            e.event(),
            "mallory",
            e.detail(),
            e.createdAt(),
            e.prevHash(),
            AuditChain.hashOf(forged)));

    assertThat(verify(entries))
        .isInstanceOfSatisfying(
            ChainVerification.Broken.class, b -> assertThat(b.seq()).isEqualTo(3));
  }

  @Test
  void detectsDeletedEntry() {
    List<AuditEntry> entries = new ArrayList<>(chain(4));
    entries.remove(1);
    assertThat(verify(entries))
        .isInstanceOfSatisfying(
            ChainVerification.Broken.class,
            b -> assertThat(b.reason()).contains("Expected sequence 2"));
  }

  @Test
  void detectsTruncatedTail() {
    List<AuditEntry> entries = chain(4);
    AuditEntry last = entries.getLast();
    assertThat(verify(entries.subList(0, 3), last.seq(), last.hash()))
        .isInstanceOfSatisfying(
            ChainVerification.Broken.class, b -> assertThat(b.reason()).contains("missing"));
  }

  @Test
  void detectsBrokenLink() {
    List<AuditEntry> entries = new ArrayList<>(chain(3));
    AuditEntry e = entries.get(1);
    entries.set(
        1,
        new AuditEntry(
            e.seq(),
            e.actionId(),
            e.event(),
            e.actor(),
            e.detail(),
            e.createdAt(),
            AuditChain.GENESIS_HASH,
            e.hash()));
    assertThat(verify(entries))
        .isInstanceOfSatisfying(
            ChainVerification.Broken.class, b -> assertThat(b.reason()).contains("link"));
  }

  @Test
  void verifierStopsAtFirstFailure() {
    List<AuditEntry> entries = new ArrayList<>(chain(3));
    ChainVerifier verifier = new ChainVerifier();
    entries.set(0, entries.get(1));
    assertThat(verifier.accept(entries.get(0))).isPresent();
    assertThat(verifier.accept(entries.get(2))).isPresent();
  }

  @Test
  void canonicalEncodingAvoidsFieldBoundaryCollisions() {
    String a = AuditChain.hash(1, null, "e", "a|b", "c", T0, AuditChain.GENESIS_HASH);
    String b = AuditChain.hash(1, null, "e", "a", "b|c", T0, AuditChain.GENESIS_HASH);
    String c = AuditChain.hash(1, 1L, "e", "a", "b|c", T0, AuditChain.GENESIS_HASH);
    assertThat(a).isNotEqualTo(b);
    assertThat(b).isNotEqualTo(c);
  }

  @Test
  void eventTypeForStatus() {
    for (var status :
        io.github.codebyjuliamendes.agentledger.domain.proposal.ProposalStatus.values()) {
      assertThat(AuditEventType.forStatus(status)).isNotNull();
    }
    assertThat(AuditEventType.POLICY_UPDATED.code()).isEqualTo("policy_updated");
  }
}
