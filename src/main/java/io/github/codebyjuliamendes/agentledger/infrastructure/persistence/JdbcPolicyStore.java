package io.github.codebyjuliamendes.agentledger.infrastructure.persistence;

import io.github.codebyjuliamendes.agentledger.application.PolicyStore;
import io.github.codebyjuliamendes.agentledger.domain.policy.RiskLevel;
import io.github.codebyjuliamendes.agentledger.domain.policy.RiskPolicy;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPolicyStore implements PolicyStore {

  private final JdbcClient jdbc;

  JdbcPolicyStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public RiskPolicy load() {
    return new RiskPolicy(terms(RiskLevel.MEDIUM), terms(RiskLevel.HIGH));
  }

  @Override
  public void save(RiskPolicy policy) {
    jdbc.sql("DELETE FROM policy_term").update();
    insert(RiskLevel.MEDIUM, policy.mediumTerms());
    insert(RiskLevel.HIGH, policy.highTerms());
  }

  private List<String> terms(RiskLevel level) {
    return jdbc.sql("SELECT term FROM policy_term WHERE risk_level = :level ORDER BY sort_order")
        .param("level", level.code())
        .query(String.class)
        .list();
  }

  private void insert(RiskLevel level, List<String> terms) {
    for (int i = 0; i < terms.size(); i++) {
      jdbc.sql(
              "INSERT INTO policy_term (risk_level, sort_order, term) VALUES (:level, :order, :term)")
          .param("level", level.code())
          .param("order", i)
          .param("term", terms.get(i))
          .update();
    }
  }
}
