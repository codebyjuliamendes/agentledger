package io.github.codebyjuliamendes.agentledger.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "agentledger.rate-limit.enabled=true",
      "agentledger.rate-limit.capacity=2",
      "agentledger.rate-limit.refill-per-minute=1"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitIntegrationTest {

  @Autowired MockMvc mvc;

  @Test
  void writesBeyondTheBurstAreRejectedWhileReadsAreNot() throws Exception {
    String body = "{\"medium\":[\"refund\"],\"high\":[\"export\"]}";
    for (int i = 0; i < 2; i++) {
      mvc.perform(put("/api/policies").contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isOk());
    }
    mvc.perform(put("/api/policies").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.type").value("urn:agentledger:problem:rate-limited"));
    mvc.perform(get("/api/policies")).andExpect(status().isOk());
  }
}
