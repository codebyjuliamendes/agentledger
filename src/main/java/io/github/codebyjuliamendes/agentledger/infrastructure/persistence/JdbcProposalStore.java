package io.github.codebyjuliamendes.agentledger.infrastructure.persistence;

import io.github.codebyjuliamendes.agentledger.application.ProposalStore;
import io.github.codebyjuliamendes.agentledger.domain.policy.ProposalContent;
import io.github.codebyjuliamendes.agentledger.domain.policy.RiskLevel;
import io.github.codebyjuliamendes.agentledger.domain.proposal.ActionProposal;
import io.github.codebyjuliamendes.agentledger.domain.proposal.ProposalStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
class JdbcProposalStore implements ProposalStore {

  private static final String COLUMNS =
      "id, agent, action, target, reason, risk, status, policy_rule, policy_detail, created_at,"
          + " reviewed_at";

  private final JdbcClient jdbc;

  JdbcProposalStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public ActionProposal insert(ActionProposal p) {
    KeyHolder keys = new GeneratedKeyHolder();
    jdbc.sql(
            """
            INSERT INTO action_proposal
              (agent, action, target, reason, risk, status, policy_rule, policy_detail,
               created_at, reviewed_at)
            VALUES (:agent, :action, :target, :reason, :risk, :status, :rule, :detail,
                    :createdAt, :reviewedAt)
            """)
        .param("agent", p.content().agent())
        .param("action", p.content().action())
        .param("target", p.content().target())
        .param("reason", p.content().reason())
        .param("risk", p.risk().code())
        .param("status", p.status().code())
        .param("rule", p.policyRule())
        .param("detail", p.policyDetail())
        .param("createdAt", Timestamps.toDb(p.createdAt()))
        .param("reviewedAt", Timestamps.toDb(p.reviewedAt()))
        .update(keys, "id");
    return p.withId(Objects.requireNonNull(keys.getKey(), "generated id").longValue());
  }

  @Override
  public Optional<ActionProposal> findById(long id) {
    return jdbc.sql("SELECT " + COLUMNS + " FROM action_proposal WHERE id = :id")
        .param("id", id)
        .query(JdbcProposalStore::map)
        .optional();
  }

  @Override
  public boolean updateStatus(ActionProposal updated, ProposalStatus expected) {
    return jdbc.sql(
                """
                UPDATE action_proposal SET status = :status, reviewed_at = :reviewedAt
                WHERE id = :id AND status = :expected
                """)
            .param("status", updated.status().code())
            .param("reviewedAt", Timestamps.toDb(updated.reviewedAt()))
            .param("id", updated.id())
            .param("expected", expected.code())
            .update()
        == 1;
  }

  @Override
  public List<ActionProposal> list(Optional<ProposalStatus> status, int limit) {
    return status
        .map(
            s ->
                jdbc.sql(
                        "SELECT "
                            + COLUMNS
                            + " FROM action_proposal WHERE status = :status ORDER BY id DESC"
                            + " LIMIT :limit")
                    .param("status", s.code()))
        .orElseGet(
            () ->
                jdbc.sql(
                    "SELECT " + COLUMNS + " FROM action_proposal ORDER BY id DESC LIMIT :limit"))
        .param("limit", limit)
        .query(JdbcProposalStore::map)
        .list();
  }

  @Override
  public ProposalSummary summary() {
    return jdbc.sql(
            """
            SELECT COUNT(*) AS total,
                   COUNT(CASE WHEN status = 'pending' THEN 1 END) AS pending,
                   COUNT(CASE WHEN status = 'approved' THEN 1 END) AS approved,
                   COUNT(CASE WHEN status = 'executed' THEN 1 END) AS executed,
                   COUNT(CASE WHEN status IN ('blocked', 'rejected') THEN 1 END) AS blocked
            FROM action_proposal
            """)
        .query(
            (rs, n) ->
                new ProposalSummary(
                    rs.getLong("total"),
                    rs.getLong("pending"),
                    rs.getLong("approved"),
                    rs.getLong("executed"),
                    rs.getLong("blocked")))
        .single();
  }

  @Override
  public long count() {
    return jdbc.sql("SELECT COUNT(*) FROM action_proposal").query(Long.class).single();
  }

  private static ActionProposal map(ResultSet rs, int row) throws SQLException {
    return new ActionProposal(
        rs.getLong("id"),
        new ProposalContent(
            rs.getString("agent"),
            rs.getString("action"),
            rs.getString("target"),
            rs.getString("reason")),
        RiskLevel.fromCode(rs.getString("risk")),
        ProposalStatus.fromCode(rs.getString("status")),
        rs.getString("policy_rule"),
        rs.getString("policy_detail"),
        Timestamps.read(rs, "created_at"),
        Timestamps.read(rs, "reviewed_at"));
  }
}
