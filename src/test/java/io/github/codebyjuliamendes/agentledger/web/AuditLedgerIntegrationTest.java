package io.github.codebyjuliamendes.agentledger.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.codebyjuliamendes.agentledger.application.AuditLedger;
import io.github.codebyjuliamendes.agentledger.application.PolicyService;
import io.github.codebyjuliamendes.agentledger.application.ProposalService;
import io.github.codebyjuliamendes.agentledger.domain.audit.ChainVerification;
import io.github.codebyjuliamendes.agentledger.domain.policy.ProposalContent;
import io.github.codebyjuliamendes.agentledger.domain.proposal.ReviewDecision;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuditLedgerIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired ProposalService proposals;
  @Autowired PolicyService policies;
  @Autowired AuditLedger ledger;
  @Autowired JdbcTemplate jdbc;
  @Autowired DataSource dataSource;

  private void someActivity() {
    proposals.submit(new ProposalContent("a", "Read profile", "c:1", "lookup"));
    long pending = proposals.submit(new ProposalContent("a", "Refund payment", "p:1", "dup")).id();
    proposals.review(pending, ReviewDecision.APPROVE, "Ana", null);
    proposals.markExecuted(pending, null, null);
    proposals.submit(new ProposalContent("a", "Drop table", "db:1", "cleanup"));
    policies.update(List.of("refund"), List.of("export"), "owner");
  }

  @Test
  void ledgerVerifiesAfterActivity() throws Exception {
    someActivity();

    assertThat(ledger.verify()).isInstanceOf(ChainVerification.Intact.class);
    mvc.perform(get("/api/audit/verify"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valid").value(true))
        .andExpect(jsonPath("$.checked").value(8))
        .andExpect(jsonPath("$.head_hash").isString());
  }

  @Test
  void policyChangesAreLedgerEntriesWithoutAction() {
    policies.update(List.of("refund"), List.of("export"), "owner");
    policies.update(List.of("refund"), List.of("export"), "owner"); // no-op, not recorded

    assertThat(
            jdbc.queryForList(
                "SELECT event FROM audit_event WHERE action_id IS NULL", String.class))
        .containsExactly("policy_updated");
  }

  @Test
  void largestAllowedPolicyFitsInTheLedger() {
    List<String> medium =
        IntStream.range(0, 30).mapToObj(i -> ("m" + i + "-").repeat(20).substring(0, 40)).toList();
    List<String> high =
        IntStream.range(0, 30).mapToObj(i -> ("h" + i + "-").repeat(20).substring(0, 40)).toList();

    policies.update(medium, high, "owner");
    proposals.submit(
        new ProposalContent("a", String.join(" ", medium).substring(0, 300), "t", "r"));

    assertThat(ledger.verify()).isInstanceOf(ChainVerification.Intact.class);
  }

  @Test
  void databaseRejectsUpdateAndDelete() {
    someActivity();

    assertThatThrownBy(() -> jdbc.update("UPDATE audit_event SET actor = 'mallory' WHERE seq = 1"))
        .isInstanceOf(DataAccessException.class)
        .hasStackTraceContaining("append-only");
    assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_event WHERE seq = 1"))
        .isInstanceOf(DataAccessException.class)
        .hasStackTraceContaining("append-only");
    assertThat(ledger.verify()).isInstanceOf(ChainVerification.Intact.class);
  }

  @Test
  void verificationDetectsTamperingThatBypassesTheTrigger() throws Exception {
    someActivity();
    dropAppendOnlyTrigger();
    jdbc.update("UPDATE audit_event SET detail = 'nothing to see' WHERE seq = 3");

    mvc.perform(get("/api/audit/verify"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valid").value(false))
        .andExpect(jsonPath("$.broken_at").value(3))
        .andExpect(jsonPath("$.checked").value(2));
  }

  @Test
  void verificationDetectsDeletedTail() {
    someActivity();
    dropAppendOnlyTrigger();
    jdbc.update("DELETE FROM audit_event WHERE seq = (SELECT MAX(seq) FROM audit_event)");

    assertThat(ledger.verify())
        .isInstanceOfSatisfying(
            ChainVerification.Broken.class, b -> assertThat(b.reason()).contains("missing"));
  }

  @Test
  void concurrentWritesKeepAContiguousChain() throws Exception {
    int writers = 24;
    try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
      List<Callable<Long>> tasks =
          IntStream.range(0, writers)
              .<Callable<Long>>mapToObj(
                  i ->
                      () ->
                          proposals
                              .submit(new ProposalContent("a" + i, "Refund", "p:" + i, "x"))
                              .id())
              .toList();
      for (Future<Long> f : pool.invokeAll(tasks)) {
        assertThat(f.get()).isPositive();
      }
    }

    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Long.class))
        .isEqualTo(writers);
    assertThat(ledger.verify())
        .isInstanceOfSatisfying(
            ChainVerification.Intact.class, v -> assertThat(v.checked()).isEqualTo(writers));
  }

  private void dropAppendOnlyTrigger() {
    try (Connection c = dataSource.getConnection()) {
      String product = c.getMetaData().getDatabaseProductName().toLowerCase();
      if (product.contains("postgres")) {
        jdbc.execute("DROP TRIGGER audit_event_no_update_delete ON audit_event");
      } else {
        jdbc.execute("DROP TRIGGER audit_event_no_update_delete");
      }
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }
}
