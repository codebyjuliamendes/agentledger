package io.github.codebyjuliamendes.agentledger.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.codebyjuliamendes.agentledger.domain.proposal.ProposalStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PolicyEngineTest {

  private static ProposalContent proposal(String action, String target, String reason) {
    return new ProposalContent("test", action, target, reason);
  }

  private static PolicyEvaluation evaluate(RiskPolicy policy, ProposalContent content) {
    return PolicyEngine.forPolicy(policy).evaluate(content);
  }

  /** Port of test/policies.test.js from the previous Node implementation. */
  @Nested
  class LegacySpecification {

    private final RiskPolicy custom = new RiskPolicy(List.of("custom"), List.of("sensitive"));

    @Test
    void configuredMediumTermRoutesToHumanReview() {
      PolicyEvaluation result =
          evaluate(custom, proposal("custom operation", "resource:1", "test policy"));

      assertThat(result.risk()).isEqualTo(RiskLevel.MEDIUM);
      assertThat(ProposalStatus.initialFor(result.outcome())).isEqualTo(ProposalStatus.PENDING);
    }

    @Test
    void criticalActionIsBlockedEvenWithCustomPolicy() {
      PolicyEvaluation result =
          evaluate(
              custom, proposal("delete production database", "db:prod", "test mandatory rule"));

      assertThat(result.risk()).isEqualTo(RiskLevel.CRITICAL);
      assertThat(result.outcome()).isInstanceOf(PolicyOutcome.Block.class);
      assertThat(ProposalStatus.initialFor(result.outcome())).isEqualTo(ProposalStatus.BLOCKED);
    }

    @Test
    void customPolicyReplacesDefaults() {
      assertThat(custom.mediumTerms()).containsExactly("custom");
      assertThat(evaluate(custom, proposal("Refund payment", "pay:1", "dup")).risk())
          .isEqualTo(RiskLevel.LOW);
    }
  }

  @Nested
  class DefaultPolicy {

    @ParameterizedTest
    @CsvSource({
      "Read customer profile, customer:c_1042, Investigate a billing question, LOW",
      "Refund payment, payment:pay_8841, Customer reports duplicate charge, MEDIUM",
      "Export customer emails, workspace:acme, Create a campaign audience, HIGH",
      "Update shipping address, order:ord_2091, Customer confirmed correction, HIGH",
      "Delete production database, db:prod-eu-1, Reset stale environment data, CRITICAL",
      "Grant role, user:42, Needs access, HIGH",
      "Send newsletter, list:all, Monthly digest, HIGH",
    })
    void classifiesSeedScenarios(String action, String target, String reason, RiskLevel risk) {
      assertThat(evaluate(RiskPolicy.DEFAULT, proposal(action, target, reason)).risk())
          .isEqualTo(risk);
    }

    @Test
    void matchesConfiguredTermsInTargetAndReasonCaseInsensitively() {
      assertThat(evaluate(RiskPolicy.DEFAULT, proposal("Read", "PAYMENT:1", "x")).risk())
          .isEqualTo(RiskLevel.MEDIUM);
      assertThat(evaluate(RiskPolicy.DEFAULT, proposal("Read", "doc:1", "then EXPORT it")).risk())
          .isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void configuredTermsMatchAsSubstringsToErrOnTheSideOfReview() {
      assertThat(evaluate(RiskPolicy.DEFAULT, proposal("Overwrite file", "fs:/tmp", "x")).risk())
          .isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void agentNameIsIgnored() {
      ProposalContent content = new ProposalContent("Delete Bot", "Read profile", "c:1", "lookup");
      assertThat(evaluate(RiskPolicy.DEFAULT, content).risk()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    void mostSevereMatchWinsAndAllMatchesAreReported() {
      PolicyEvaluation result =
          evaluate(RiskPolicy.DEFAULT, proposal("Export and delete payments", "db:x", "cleanup"));

      assertThat(result.risk()).isEqualTo(RiskLevel.CRITICAL);
      assertThat(result.ruleId()).isEqualTo(DestructiveActionRule.ID);
      assertThat(result.matches())
          .extracting(RuleMatch::risk)
          .containsExactly(RiskLevel.CRITICAL, RiskLevel.HIGH, RiskLevel.MEDIUM);
      assertThat(result.explanation()).contains("critical").contains("delete");
    }

    @Test
    void lowRiskHasDefaultRuleAndExplanation() {
      PolicyEvaluation result = evaluate(RiskPolicy.DEFAULT, proposal("Read", "doc:1", "lookup"));

      assertThat(result.ruleId()).isEqualTo(PolicyEvaluation.DEFAULT_RULE_ID);
      assertThat(result.decisive()).isEmpty();
      assertThat(result.outcome()).isInstanceOf(PolicyOutcome.AutoApprove.class);
      assertThat(result.explanation()).contains("low risk");
    }
  }

  @Nested
  class MandatoryDestructiveRule {

    @ParameterizedTest
    @ValueSource(
        strings = {
          "delete rows",
          "Deleting old backups",
          "deleted users",
          "DROP TABLE users",
          "dropping the index",
          "remove member",
          "removal of keys",
          "erase disk",
          "destroy cluster",
          "truncate audit table",
          "wipe laptop",
          "purge cache",
          "grant admin access",
          "add administrator",
          "remoção não; delete sim"
        })
    void blocksDestructiveVerbsAndInflections(String action) {
      assertThat(evaluate(RiskPolicy.DEFAULT, proposal(action, "x:1", "y")).risk())
          .isEqualTo(RiskLevel.CRITICAL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Open dropdown menu", "Read administrative report", "undeletable flag"})
    void doesNotBlockWordsThatOnlyContainADestructiveStem(String action) {
      assertThat(evaluate(RiskPolicy.DEFAULT, proposal(action, "ui:1", "y")).risk())
          .isNotEqualTo(RiskLevel.CRITICAL);
    }

    @Test
    void cannotBeDisabledByEmptyPolicy() {
      RiskPolicy empty = new RiskPolicy(List.of(), List.of());
      assertThat(evaluate(empty, proposal("drop schema", "db:1", "y")).risk())
          .isEqualTo(RiskLevel.CRITICAL);
    }
  }

  @Test
  void acceptsCustomRules() {
    PolicyRule prodTargets =
        new PolicyRule() {
          @Override
          public String id() {
            return "prod-target";
          }

          @Override
          public Optional<RuleMatch> evaluate(ProposalContent content) {
            return content.target().startsWith("prod:")
                ? Optional.of(new RuleMatch(id(), RiskLevel.HIGH, List.of(content.target())))
                : Optional.empty();
          }
        };
    PolicyEngine engine = new PolicyEngine(List.of(prodTargets));

    assertThat(engine.evaluate(proposal("Read", "prod:db", "x")).risk()).isEqualTo(RiskLevel.HIGH);
    assertThat(engine.evaluate(proposal("Read", "dev:db", "x")).risk()).isEqualTo(RiskLevel.LOW);
  }

  @Test
  void contentIsTrimmed() {
    ProposalContent content = new ProposalContent("  a ", " b ", " c", "d  ");
    assertThat(content).isEqualTo(new ProposalContent("a", "b", "c", "d"));
  }
}
