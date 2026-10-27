package io.github.codebyjuliamendes.agentledger.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.codebyjuliamendes.agentledger.application.AuditLedger;
import io.github.codebyjuliamendes.agentledger.application.ProposalService;
import io.github.codebyjuliamendes.agentledger.domain.audit.ChainVerification;
import io.github.codebyjuliamendes.agentledger.domain.proposal.ActionProposal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "agentledger.seed-demo-data=true")
@ActiveProfiles("test")
class DemoSeedIntegrationTest {

  @Autowired ProposalService proposals;
  @Autowired AuditLedger ledger;

  @Test
  void seedsOneOfEachOutcomeWithAValidChain() {
    assertThat(proposals.list(Optional.empty(), 100))
        .extracting(p -> p.content().action() + "=" + p.status().code())
        .containsExactly(
            "Delete production database=blocked",
            "Update shipping address=pending",
            "Export customer emails=rejected",
            "Refund payment=pending",
            "Read customer profile=executed");
    assertThat(proposals.summary().total()).isEqualTo(5);
    assertThat(ledger.verify()).isInstanceOf(ChainVerification.Intact.class);
    ActionProposal export = proposals.list(Optional.empty(), 100).get(2);
    assertThat(proposals.trail(export.id())).hasSize(2);
  }
}
