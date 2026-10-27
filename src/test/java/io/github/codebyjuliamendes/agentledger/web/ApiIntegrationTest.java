package io.github.codebyjuliamendes.agentledger.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.codebyjuliamendes.agentledger.web.filter.SecurityHeadersFilter;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  private ResultActions postJson(String url, Object body) throws Exception {
    return mvc.perform(
        post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
  }

  private JsonNode propose(String action, String target, String reason) throws Exception {
    String body =
        postJson(
                "/api/actions",
                Map.of("agent", "test", "action", action, "target", target, "reason", reason))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body);
  }

  private void restoreDefaultPolicy() throws Exception {
    mvc.perform(
            put("/api/policies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"medium":["refund","payment"],
                     "high":["export","email","permission","role","update","write","send"]}
                    """))
        .andExpect(status().isOk());
  }

  /** Port of test/policies.test.js. */
  @Test
  void configuredTermsRouteToHumanReviewAndCriticalActionsRemainBlocked() throws Exception {
    mvc.perform(
            put("/api/policies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"medium\":[\"custom\"],\"high\":[\"sensitive\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.medium[0]").value("custom"))
        .andExpect(jsonPath("$.medium", hasSize(1)));
    try {
      JsonNode medium = propose("custom operation", "resource:1", "test policy");
      assertThat(medium.get("risk").asText()).isEqualTo("medium");
      assertThat(medium.get("status").asText()).isEqualTo("pending");

      JsonNode critical = propose("delete production database", "db:prod", "test mandatory rule");
      assertThat(critical.get("status").asText()).isEqualTo("blocked");

      postJson(
              "/api/actions/" + critical.get("id").asLong() + "/review",
              Map.of("decision", "approve"))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.error").value(containsString("mandatory safety policy")));
    } finally {
      restoreDefaultPolicy();
    }
  }

  @Nested
  class Proposals {

    @Test
    void lowRiskIsAutoExecutedWithTwoLedgerEntries() throws Exception {
      JsonNode low = propose("Read customer profile", "customer:1", "billing question");
      long id = low.get("id").asLong();

      assertThat(low.get("risk").asText()).isEqualTo("low");
      assertThat(low.get("status").asText()).isEqualTo("executed");
      assertThat(low.get("policy_rule").asText()).isEqualTo("default-low");
      assertThat(low.get("created_at").asText()).endsWith("Z");
      assertThat(low.has("reviewed_at")).isTrue();

      mvc.perform(get("/api/actions/" + id + "/events"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$", hasSize(2)))
          .andExpect(jsonPath("$[0].event").value("executed"))
          .andExpect(jsonPath("$[0].actor").value("policy engine"))
          .andExpect(jsonPath("$[1].event").value("proposed"))
          .andExpect(jsonPath("$[1].actor").value("test"))
          .andExpect(jsonPath("$[0].action_id").value(id))
          .andExpect(jsonPath("$[0].hash", matchesPattern("[0-9a-f]{64}")));
    }

    @Test
    void createReturnsLocationAndCanBeFetched() throws Exception {
      String location =
          postJson(
                  "/api/actions",
                  Map.of("agent", " a ", "action", "Read", "target", "doc:1", "reason", "why"))
              .andExpect(status().isCreated())
              .andExpect(header().string("Location", startsWith("/api/actions/")))
              .andExpect(jsonPath("$.agent").value("a"))
              .andReturn()
              .getResponse()
              .getHeader("Location");
      mvc.perform(get(location))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.agent").value("a"));
    }

    @Test
    void approveThenReportExecution() throws Exception {
      long id = propose("Refund payment", "payment:1", "duplicate charge").get("id").asLong();

      postJson(
              "/api/actions/" + id + "/review",
              Map.of("decision", "approve", "reviewer", "Ana", "note", "Verified duplicate."))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("approved"))
          .andExpect(jsonPath("$.reviewed_at").isNotEmpty());

      postJson("/api/actions/" + id + "/review", Map.of("decision", "deny"))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.detail").value("Only pending actions can be reviewed."))
          .andExpect(jsonPath("$.from").value("approved"));

      mvc.perform(post("/api/actions/" + id + "/execution"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("executed"));

      mvc.perform(get("/api/actions/" + id + "/events"))
          .andExpect(jsonPath("$", hasSize(3)))
          .andExpect(jsonPath("$[0].event").value("executed"))
          .andExpect(jsonPath("$[0].actor").value("test"))
          .andExpect(jsonPath("$[1].event").value("approved"))
          .andExpect(jsonPath("$[1].actor").value("Ana"))
          .andExpect(jsonPath("$[1].detail").value("Verified duplicate."));
    }

    @Test
    void denyUsesDefaultReviewerAndNote() throws Exception {
      long id = propose("Export report", "workspace:1", "audit").get("id").asLong();

      postJson("/api/actions/" + id + "/review", Map.of("decision", "deny"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("rejected"));

      mvc.perform(get("/api/actions/" + id + "/events"))
          .andExpect(jsonPath("$[0].event").value("rejected"))
          .andExpect(jsonPath("$[0].actor").value("workspace owner"))
          .andExpect(jsonPath("$[0].detail").value(containsString("rejected the proposed action")));
    }

    @Test
    void executionOfPendingProposalIsRejected() throws Exception {
      long id = propose("Refund payment", "payment:2", "dup").get("id").asLong();
      mvc.perform(
              post("/api/actions/" + id + "/execution")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{}"))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.type").value("urn:agentledger:problem:invalid-transition"));
    }

    @Test
    void listFiltersByStatusAndSummaryCounts() throws Exception {
      propose("Refund payment", "payment:3", "dup");
      mvc.perform(get("/api/actions").param("status", "pending"))
          .andExpect(status().isOk())
          .andExpect(
              jsonPath("$[*].status")
                  .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("pending"))));
      mvc.perform(get("/api/actions").param("status", "nope"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error").value("Unknown status filter."));
      mvc.perform(get("/api/actions").param("limit", "0")).andExpect(status().isBadRequest());

      String summary =
          mvc.perform(get("/api/summary"))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      JsonNode s = json.readTree(summary);
      assertThat(s.get("total").asLong()).isPositive();
      assertThat(s.get("pending").asLong()).isPositive();
      assertThat(s.has("executed")).isTrue();
      assertThat(s.has("blocked")).isTrue();
    }
  }

  @Nested
  class Errors {

    @Test
    void missingFieldsReturnProblemDetails() throws Exception {
      postJson("/api/actions", Map.of("agent", "a", "action", "  "))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.status").value(400))
          .andExpect(jsonPath("$.title").value("Invalid request"))
          .andExpect(jsonPath("$.errors", hasSize(3)))
          .andExpect(jsonPath("$.error").value(containsString("Provide an action.")));
    }

    @Test
    void overlongFieldIsRejected() throws Exception {
      postJson(
              "/api/actions",
              Map.of("agent", "a", "action", "x".repeat(301), "target", "t", "reason", "r"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error").value(containsString("too long")));
    }

    @Test
    void malformedJson() throws Exception {
      mvc.perform(post("/api/actions").contentType(MediaType.APPLICATION_JSON).content("{nope"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.type").value("urn:agentledger:problem:malformed-body"));
    }

    @Test
    void reviewWithoutBodyOrWithBadDecision() throws Exception {
      long id = propose("Refund payment", "payment:9", "dup").get("id").asLong();
      mvc.perform(post("/api/actions/" + id + "/review").contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isBadRequest());
      postJson("/api/actions/" + id + "/review", Map.of("decision", "maybe"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error").value("Choose approve or deny."));
      postJson(
              "/api/actions/" + id + "/review",
              Map.of("decision", "approve", "note", "n".repeat(501)))
          .andExpect(status().isBadRequest());
    }

    @Test
    void unknownProposal() throws Exception {
      postJson("/api/actions/999999/review", Map.of("decision", "approve"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.error").value("Action 999999 not found."));
      mvc.perform(get("/api/actions/999999/events")).andExpect(status().isNotFound());
      mvc.perform(get("/api/actions/abc")).andExpect(status().isBadRequest());
    }

    @Test
    void wrongContentType() throws Exception {
      mvc.perform(post("/api/actions").contentType(MediaType.TEXT_PLAIN).content("hi"))
          .andExpect(status().isUnsupportedMediaType())
          .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void unknownRouteIsProblem() throws Exception {
      mvc.perform(get("/api/nothing-here"))
          .andExpect(status().isNotFound())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void invalidPolicyIsRejectedAndNotSaved() throws Exception {
      mvc.perform(
              put("/api/policies")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"medium\":[\"x\"],\"high\":[]}"))
          .andExpect(status().isBadRequest())
          .andExpect(
              jsonPath("$.error")
                  .value("Provide arrays of up to 30 risk terms, each 2–40 characters."));
      mvc.perform(
              put("/api/policies")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"medium\":[\"refund\"]}"))
          .andExpect(status().isBadRequest());
      mvc.perform(get("/api/policies"))
          .andExpect(jsonPath("$.medium[0]").value("refund"))
          .andExpect(jsonPath("$.critical[0]").value("delete"));
    }

    @Test
    void oversizedBodyIsRejected() throws Exception {
      String big = "{\"agent\":\"" + "a".repeat(20_000) + "\"}";
      mvc.perform(post("/api/actions").contentType(MediaType.APPLICATION_JSON).content(big))
          .andExpect(status().isPayloadTooLarge())
          .andExpect(jsonPath("$.type").value("urn:agentledger:problem:payload-too-large"));
    }
  }

  @Nested
  class Platform {

    @Test
    void healthEndpoints() throws Exception {
      mvc.perform(get("/api/health"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("ok"));
      mvc.perform(get("/actuator/health"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("UP"));
      mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
      mvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
    }

    @Test
    void securityHeadersAndCorrelationId() throws Exception {
      mvc.perform(get("/index.html").header("X-Request-Id", "req-123"))
          .andExpect(status().isOk())
          .andExpect(header().string("X-Request-Id", "req-123"))
          .andExpect(
              header().string("Content-Security-Policy", containsString("script-src 'self'")))
          .andExpect(
              header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
          .andExpect(header().string("X-Content-Type-Options", "nosniff"))
          .andExpect(header().string("X-Frame-Options", "DENY"))
          .andExpect(content().string(containsString("AgentLedger")));
      mvc.perform(get("/api/summary").header("X-Request-Id", "bad id\nwith newline"))
          .andExpect(header().string("X-Request-Id", matchesPattern("[0-9a-f-]{36}")))
          .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void cspHasNoUnsafeInline() {
      assertThat(SecurityHeadersFilter.CSP).doesNotContain("unsafe");
    }

    @Test
    void staticAssetsAreServed() throws Exception {
      mvc.perform(get("/app.js")).andExpect(status().isOk());
      mvc.perform(get("/theme.js")).andExpect(status().isOk());
      mvc.perform(get("/style.css")).andExpect(status().isOk());
      mvc.perform(get("/mark-al.svg")).andExpect(status().isOk());
    }
  }
}
