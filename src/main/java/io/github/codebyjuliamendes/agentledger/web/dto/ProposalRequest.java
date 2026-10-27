package io.github.codebyjuliamendes.agentledger.web.dto;

import io.github.codebyjuliamendes.agentledger.domain.policy.ProposalContent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProposalRequest(
    @NotBlank(message = "Provide an agent name.")
        @Size(max = 300, message = "Agent name is too long (max 300).")
        String agent,
    @NotBlank(message = "Provide an action.")
        @Size(max = 300, message = "Action is too long (max 300).")
        String action,
    @NotBlank(message = "Provide a target resource.")
        @Size(max = 300, message = "Target is too long (max 300).")
        String target,
    @NotBlank(message = "Provide a reason.")
        @Size(max = 300, message = "Reason is too long (max 300).")
        String reason) {

  public ProposalContent toContent() {
    return new ProposalContent(agent, action, target, reason);
  }
}
