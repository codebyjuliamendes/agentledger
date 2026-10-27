package io.github.codebyjuliamendes.agentledger.web.filter;

import io.github.codebyjuliamendes.agentledger.config.AgentLedgerProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-client token bucket for API writes (POST/PUT/PATCH/DELETE). In-memory, so limits are per
 * instance; use a gateway or a shared store when running several replicas. The client key is the
 * remote address, which honours {@code X-Forwarded-For} only when {@code
 * server.forward-headers-strategy} is enabled behind a trusted proxy.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
public class WriteRateLimitFilter extends OncePerRequestFilter {

  private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

  private final AgentLedgerProperties.RateLimit config;
  private final ProblemWriter problems;
  private final Clock clock;
  private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

  public WriteRateLimitFilter(
      AgentLedgerProperties properties, ProblemWriter problems, Clock clock) {
    this.config = properties.rateLimit();
    this.problems = problems;
    this.clock = clock;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !config.enabled()
        || !WRITE_METHODS.contains(request.getMethod())
        || !request.getRequestURI().startsWith("/api/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long now = clock.millis();
    if (buckets.size() >= config.maxClients()) {
      evictIdle(now);
    }
    Bucket bucket =
        buckets.computeIfAbsent(request.getRemoteAddr(), k -> new Bucket(config.capacity(), now));
    long waitMillis = bucket.tryConsume(now, config.capacity(), refillPerMilli());
    response.setHeader("X-RateLimit-Limit", Integer.toString(config.capacity()));
    if (waitMillis > 0) {
      response.setHeader("Retry-After", Long.toString(Math.max(1, (waitMillis + 999) / 1000)));
      problems.write(
          response,
          HttpStatus.TOO_MANY_REQUESTS,
          "rate-limited",
          "Too many write requests. Try again shortly.");
      return;
    }
    chain.doFilter(request, response);
  }

  private double refillPerMilli() {
    return config.refillPerMinute() / 60_000.0;
  }

  /** Drops buckets that have refilled completely; if still too many, starts over. */
  private void evictIdle(long now) {
    buckets.values().removeIf(b -> b.isFull(now, config.capacity(), refillPerMilli()));
    if (buckets.size() >= config.maxClients()) {
      buckets.clear();
    }
  }

  static final class Bucket {
    private double tokens;
    private long updatedAt;

    Bucket(double tokens, long now) {
      this.tokens = tokens;
      this.updatedAt = now;
    }

    /** Returns 0 when a token was taken, otherwise milliseconds until one is available. */
    synchronized long tryConsume(long now, int capacity, double perMilli) {
      refill(now, capacity, perMilli);
      if (tokens >= 1) {
        tokens -= 1;
        return 0;
      }
      return (long) Math.ceil((1 - tokens) / perMilli);
    }

    synchronized boolean isFull(long now, int capacity, double perMilli) {
      refill(now, capacity, perMilli);
      return tokens >= capacity;
    }

    private void refill(long now, int capacity, double perMilli) {
      tokens = Math.min(capacity, tokens + (now - updatedAt) * perMilli);
      updatedAt = Math.max(updatedAt, now);
    }
  }
}
