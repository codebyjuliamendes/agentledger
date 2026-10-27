package io.github.codebyjuliamendes.agentledger.web;

import io.github.codebyjuliamendes.agentledger.application.ProposalService;
import io.github.codebyjuliamendes.agentledger.domain.proposal.ProposalStatus;
import io.github.codebyjuliamendes.agentledger.domain.proposal.ReviewDecision;
import io.github.codebyjuliamendes.agentledger.web.dto.AuditEventResponse;
import io.github.codebyjuliamendes.agentledger.web.dto.ExecutionReport;
import io.github.codebyjuliamendes.agentledger.web.dto.ProposalRequest;
import io.github.codebyjuliamendes.agentledger.web.dto.ProposalResponse;
import io.github.codebyjuliamendes.agentledger.web.dto.ReviewRequest;
import io.github.codebyjuliamendes.agentledger.web.dto.SummaryResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
class ActionController {

  private final ProposalService proposals;

  ActionController(ProposalService proposals) {
    this.proposals = proposals;
  }

  @GetMapping("/actions")
  List<ProposalResponse> list(
      @RequestParam(required = false)
          @Pattern(
              regexp = "pending|approved|rejected|executed|blocked",
              message = "Unknown status filter.")
          String status,
      @RequestParam(defaultValue = "500") @Min(1) @Max(1000) int limit) {
    return proposals.list(Optional.ofNullable(status).map(ProposalStatus::fromCode), limit).stream()
        .map(ProposalResponse::from)
        .toList();
  }

  @PostMapping(path = "/actions", consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<ProposalResponse> create(@Valid @RequestBody ProposalRequest request) {
    ProposalResponse body = ProposalResponse.from(proposals.submit(request.toContent()));
    return ResponseEntity.created(URI.create("/api/actions/" + body.id())).body(body);
  }

  @GetMapping("/actions/{id}")
  ProposalResponse get(@PathVariable long id) {
    return ProposalResponse.from(proposals.get(id));
  }

  @GetMapping("/actions/{id}/events")
  List<AuditEventResponse> events(@PathVariable long id) {
    return proposals.trail(id).stream().map(AuditEventResponse::from).toList();
  }

  @PostMapping(path = "/actions/{id}/review", consumes = MediaType.APPLICATION_JSON_VALUE)
  ProposalResponse review(@PathVariable long id, @Valid @RequestBody ReviewRequest request) {
    return ProposalResponse.from(
        proposals.review(
            id, ReviewDecision.fromCode(request.decision()), request.reviewer(), request.note()));
  }

  @PostMapping("/actions/{id}/execution")
  ProposalResponse execution(
      @PathVariable long id, @Valid @RequestBody(required = false) ExecutionReport report) {
    ExecutionReport r = report == null ? new ExecutionReport(null, null) : report;
    return ProposalResponse.from(proposals.markExecuted(id, r.actor(), r.note()));
  }

  @GetMapping("/summary")
  SummaryResponse summary() {
    return SummaryResponse.from(proposals.summary());
  }
}
