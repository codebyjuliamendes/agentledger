package io.github.codebyjuliamendes.agentledger.web.filter;

import io.github.codebyjuliamendes.agentledger.config.AgentLedgerProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Caps API request bodies. Declared lengths are rejected up front; chunked bodies are counted while
 * they are read.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class RequestBodyLimitFilter extends OncePerRequestFilter {

  private final long maxBytes;
  private final ProblemWriter problems;

  public RequestBodyLimitFilter(AgentLedgerProperties properties, ProblemWriter problems) {
    this.maxBytes = properties.http().maxBodyBytes();
    this.problems = problems;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getContentLengthLong() > maxBytes) {
      problems.write(
          response,
          HttpStatus.PAYLOAD_TOO_LARGE,
          "payload-too-large",
          "Request body exceeds " + maxBytes + " bytes.");
      return;
    }
    chain.doFilter(new LimitedRequest(request, maxBytes), response);
  }

  /** Thrown while reading a body that grows past the limit. */
  public static final class PayloadTooLargeException extends IOException {
    PayloadTooLargeException(long max) {
      super("Request body exceeds " + max + " bytes.");
    }
  }

  private static final class LimitedRequest extends HttpServletRequestWrapper {
    private final long max;
    private ServletInputStream stream;

    LimitedRequest(HttpServletRequest request, long max) {
      super(request);
      this.max = max;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
      if (stream == null) {
        stream = new LimitedStream(super.getInputStream(), max);
      }
      return stream;
    }
  }

  private static final class LimitedStream extends ServletInputStream {
    private final ServletInputStream delegate;
    private final long max;
    private long count;

    LimitedStream(ServletInputStream delegate, long max) {
      this.delegate = delegate;
      this.max = max;
    }

    @Override
    public int read() throws IOException {
      int b = delegate.read();
      if (b >= 0) {
        count(1);
      }
      return b;
    }

    @Override
    public int read(byte[] buffer, int off, int len) throws IOException {
      int n = delegate.read(buffer, off, len);
      if (n > 0) {
        count(n);
      }
      return n;
    }

    private void count(int n) throws PayloadTooLargeException {
      count += n;
      if (count > max) {
        throw new PayloadTooLargeException(max);
      }
    }

    @Override
    public boolean isFinished() {
      return delegate.isFinished();
    }

    @Override
    public boolean isReady() {
      return delegate.isReady();
    }

    @Override
    public void setReadListener(ReadListener listener) {
      delegate.setReadListener(listener);
    }
  }
}
