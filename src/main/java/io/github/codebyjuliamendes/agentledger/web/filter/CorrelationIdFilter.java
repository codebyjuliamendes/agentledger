package io.github.codebyjuliamendes.agentledger.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Propagates {@code X-Request-Id} (or generates one) into the MDC so every log line of a request
 * carries {@code correlation_id}; also writes one access-log line per API request.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

  public static final String HEADER = "X-Request-Id";
  public static final String MDC_KEY = "correlation_id";
  private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
  private static final Logger access = LoggerFactory.getLogger("agentledger.access");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String incoming = request.getHeader(HEADER);
    // Reject arbitrary client input to prevent log injection.
    String id =
        incoming != null && SAFE_ID.matcher(incoming).matches()
            ? incoming
            : UUID.randomUUID().toString();
    MDC.put(MDC_KEY, id);
    response.setHeader(HEADER, id);
    long start = System.nanoTime();
    try {
      chain.doFilter(request, response);
    } finally {
      if (request.getRequestURI().startsWith("/api/")) {
        access.info(
            "{} {} -> {} in {} ms",
            request.getMethod(),
            request.getRequestURI(),
            response.getStatus(),
            (System.nanoTime() - start) / 1_000_000);
      }
      MDC.remove(MDC_KEY);
    }
  }
}
