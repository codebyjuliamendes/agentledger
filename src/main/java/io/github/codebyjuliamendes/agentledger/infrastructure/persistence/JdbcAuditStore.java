package io.github.codebyjuliamendes.agentledger.infrastructure.persistence;

import io.github.codebyjuliamendes.agentledger.application.AuditStore;
import io.github.codebyjuliamendes.agentledger.domain.audit.AuditEntry;
import io.github.codebyjuliamendes.agentledger.domain.audit.AuditHead;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAuditStore implements AuditStore {

  private static final String COLUMNS =
      "seq, action_id, event, actor, detail, created_at, prev_hash, hash";

  private final JdbcClient jdbc;

  JdbcAuditStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public AuditHead lockHead() {
    return jdbc.sql("SELECT seq, hash FROM audit_head WHERE id = 1 FOR UPDATE")
        .query((rs, n) -> new AuditHead(rs.getLong("seq"), rs.getString("hash")))
        .single();
  }

  @Override
  public AuditHead head() {
    return jdbc.sql("SELECT seq, hash FROM audit_head WHERE id = 1")
        .query((rs, n) -> new AuditHead(rs.getLong("seq"), rs.getString("hash")))
        .single();
  }

  @Override
  public void append(AuditEntry e) {
    jdbc.sql(
            """
            INSERT INTO audit_event (seq, action_id, event, actor, detail, created_at, prev_hash, hash)
            VALUES (:seq, :actionId, :event, :actor, :detail, :createdAt, :prevHash, :hash)
            """)
        .param("seq", e.seq())
        .param("actionId", e.actionId())
        .param("event", e.event())
        .param("actor", e.actor())
        .param("detail", e.detail())
        .param("createdAt", Timestamps.toDb(e.createdAt()))
        .param("prevHash", e.prevHash())
        .param("hash", e.hash())
        .update();
    int moved =
        jdbc.sql("UPDATE audit_head SET seq = :seq, hash = :hash WHERE id = 1 AND seq = :previous")
            .param("seq", e.seq())
            .param("hash", e.hash())
            .param("previous", e.seq() - 1)
            .update();
    if (moved != 1) {
      throw new IllegalStateException("Audit head moved concurrently; the head lock was not held");
    }
  }

  @Override
  public List<AuditEntry> forAction(long actionId) {
    return jdbc.sql(
            "SELECT " + COLUMNS + " FROM audit_event WHERE action_id = :id ORDER BY seq DESC")
        .param("id", actionId)
        .query(JdbcAuditStore::map)
        .list();
  }

  @Override
  public List<AuditEntry> page(long afterSeq, int limit) {
    return jdbc.sql(
            "SELECT " + COLUMNS + " FROM audit_event WHERE seq > :after ORDER BY seq LIMIT :limit")
        .param("after", afterSeq)
        .param("limit", limit)
        .query(JdbcAuditStore::map)
        .list();
  }

  private static AuditEntry map(ResultSet rs, int row) throws SQLException {
    Long actionId = rs.getObject("action_id", Long.class);
    return new AuditEntry(
        rs.getLong("seq"),
        actionId,
        rs.getString("event"),
        rs.getString("actor"),
        rs.getString("detail"),
        Timestamps.read(rs, "created_at"),
        rs.getString("prev_hash"),
        rs.getString("hash"));
  }
}
