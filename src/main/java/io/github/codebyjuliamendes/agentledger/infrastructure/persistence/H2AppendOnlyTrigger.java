package io.github.codebyjuliamendes.agentledger.infrastructure.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import org.h2.api.Trigger;

/**
 * H2 counterpart of the PostgreSQL trigger that makes {@code audit_event} append-only. Used only by
 * the dev profile and the default test suite.
 */
public class H2AppendOnlyTrigger implements Trigger {

  @Override
  public void fire(Connection conn, Object[] oldRow, Object[] newRow) throws SQLException {
    throw new SQLException("audit_event is append-only", "42501");
  }
}
