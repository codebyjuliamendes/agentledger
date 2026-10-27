package io.github.codebyjuliamendes.agentledger.config;

import io.github.codebyjuliamendes.agentledger.application.ProposalService;
import io.github.codebyjuliamendes.agentledger.application.ProposalStore;
import io.github.codebyjuliamendes.agentledger.domain.policy.ProposalContent;
import io.github.codebyjuliamendes.agentledger.domain.proposal.ActionProposal;
import io.github.codebyjuliamendes.agentledger.domain.proposal.ReviewDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Seeds an empty workspace with sample proposals. They go through the real policy engine and
 * ledger, so the demo data has a valid hash chain. Disable with {@code
 * AGENTLEDGER_SEED_DEMO_DATA=false}.
 */
@Component
@ConditionalOnProperty(name = "agentledger.seed-demo-data", havingValue = "true")
class DemoDataSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

  private final ProposalService proposals;
  private final ProposalStore store;

  DemoDataSeeder(ProposalService proposals, ProposalStore store) {
    this.proposals = proposals;
    this.store = store;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (store.count() > 0) {
      return;
    }
    proposals.submit(
        new ProposalContent(
            "Support Agent",
            "Read customer profile",
            "customer:c_1042",
            "Investigate a billing question"));
    proposals.submit(
        new ProposalContent(
            "Ops Assistant",
            "Refund payment",
            "payment:pay_8841",
            "Customer reports duplicate charge"));
    ActionProposal export =
        proposals.submit(
            new ProposalContent(
                "Data Agent",
                "Export customer emails",
                "workspace:acme",
                "Create a campaign audience"));
    proposals.review(
        export.id(),
        ReviewDecision.DENY,
        "Security reviewer",
        "Bulk export of customer emails is not allowed for campaigns.");
    proposals.submit(
        new ProposalContent(
            "Support Agent",
            "Update shipping address",
            "order:ord_2091",
            "Customer confirmed correction"));
    proposals.submit(
        new ProposalContent(
            "Dev Agent",
            "Delete production database",
            "db:prod-eu-1",
            "Reset stale environment data"));
    log.info("Seeded demo proposals");
  }
}
