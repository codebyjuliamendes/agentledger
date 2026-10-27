package io.github.codebyjuliamendes.agentledger.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Maps {@link Instant} to TIMESTAMP WITH TIME ZONE portably (PostgreSQL and H2). */
final class Timestamps {

  private Timestamps() {}

  static OffsetDateTime toDb(Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
  }

  static Instant read(ResultSet rs, String column) throws SQLException {
    OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }
}
