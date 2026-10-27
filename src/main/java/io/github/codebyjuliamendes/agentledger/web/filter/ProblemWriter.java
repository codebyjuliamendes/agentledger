package io.github.codebyjuliamendes.agentledger.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/** Writes Problem Details from servlet filters, which run outside Spring MVC error handling. */
@Component
public class ProblemWriter {

  private final ObjectMapper mapper;

  public ProblemWriter(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public void write(HttpServletResponse response, HttpStatus status, String type, String detail)
      throws IOException {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
    pd.setType(URI.create("urn:agentledger:problem:" + type));
    pd.setTitle(status.getReasonPhrase());
    pd.setProperty("error", detail);
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    mapper.writeValue(response.getOutputStream(), pd);
  }
}
