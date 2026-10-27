package io.github.codebyjuliamendes.agentledger.web;

import java.util.Locale;
import java.util.Map;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Legacy health route kept for existing probes; delegates to Actuator. */
@RestController
class HealthController {

  private final HealthEndpoint health;

  HealthController(HealthEndpoint health) {
    this.health = health;
  }

  @GetMapping(path = "/api/health", produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<Map<String, String>> health() {
    Status status = health.health().getStatus();
    boolean up = Status.UP.equals(status);
    return ResponseEntity.status(up ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
        .body(Map.of("status", up ? "ok" : status.getCode().toLowerCase(Locale.ROOT)));
  }
}
