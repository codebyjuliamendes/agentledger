package io.github.codebyjuliamendes.agentledger.web;

import io.github.codebyjuliamendes.agentledger.application.PolicyService;
import io.github.codebyjuliamendes.agentledger.web.dto.PolicyRequest;
import io.github.codebyjuliamendes.agentledger.web.dto.PolicyResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/policies", produces = MediaType.APPLICATION_JSON_VALUE)
class PolicyController {

  static final String POLICY_ACTOR = "workspace owner";

  private final PolicyService policies;

  PolicyController(PolicyService policies) {
    this.policies = policies;
  }

  @GetMapping
  PolicyResponse get() {
    return PolicyResponse.from(policies.current());
  }

  @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  PolicyResponse update(@Valid @RequestBody PolicyRequest request) {
    return PolicyResponse.from(policies.update(request.medium(), request.high(), POLICY_ACTOR));
  }
}
