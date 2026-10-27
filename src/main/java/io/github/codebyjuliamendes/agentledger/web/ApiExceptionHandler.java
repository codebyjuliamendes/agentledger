package io.github.codebyjuliamendes.agentledger.web;

import io.github.codebyjuliamendes.agentledger.domain.policy.InvalidPolicyException;
import io.github.codebyjuliamendes.agentledger.domain.proposal.ProposalException;
import io.github.codebyjuliamendes.agentledger.web.filter.RequestBodyLimitFilter;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Renders every error as RFC 9457 Problem Details. Each body also carries an {@code error} member
 * with the human-readable message, kept for clients of the previous API.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
  static final String TYPE_BASE = "urn:agentledger:problem:";

  @ExceptionHandler(ProposalException.class)
  ProblemDetail handleProposal(ProposalException ex) {
    return switch (ex) {
      case ProposalException.NotFound nf ->
          problem(HttpStatus.NOT_FOUND, "action-not-found", "Action not found", nf.getMessage());
      case ProposalException.Blocked b ->
          problem(
              HttpStatus.CONFLICT, "action-blocked", "Action blocked by policy", b.getMessage());
      case ProposalException.InvalidTransition it ->
          withProperty(
              withProperty(
                  problem(
                      HttpStatus.CONFLICT,
                      "invalid-transition",
                      "Invalid status transition",
                      it.getMessage()),
                  "from",
                  it.from().code()),
              "to",
              it.to().code());
    };
  }

  @ExceptionHandler(InvalidPolicyException.class)
  ProblemDetail handlePolicy(InvalidPolicyException ex) {
    return problem(
        HttpStatus.BAD_REQUEST, "invalid-policy", "Invalid risk policy", ex.getMessage());
  }

  @ExceptionHandler(RequestBodyLimitFilter.PayloadTooLargeException.class)
  ProblemDetail handleTooLarge(RequestBodyLimitFilter.PayloadTooLargeException ex) {
    return problem(
        HttpStatus.PAYLOAD_TOO_LARGE, "payload-too-large", "Payload too large", ex.getMessage());
  }

  @ExceptionHandler(Exception.class)
  ProblemDetail handleUnexpected(Exception ex) {
    log.error("Unhandled error", ex);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "internal-error",
        "Internal error",
        "Something went wrong. The request was not completed.");
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<String> messages =
        ex.getBindingResult().getAllErrors().stream()
            .map(MessageSourceResolvable::getDefaultMessage)
            .distinct()
            .sorted()
            .toList();
    ProblemDetail body =
        problem(
            HttpStatus.BAD_REQUEST, "validation", "Invalid request", String.join(" ", messages));
    body.setProperty("errors", messages);
    return ResponseEntity.badRequest().headers(headers).body(body);
  }

  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<String> messages =
        ex.getAllErrors().stream()
            .map(MessageSourceResolvable::getDefaultMessage)
            .distinct()
            .toList();
    ProblemDetail body =
        problem(
            HttpStatus.BAD_REQUEST, "validation", "Invalid request", String.join(" ", messages));
    body.setProperty("errors", messages);
    return ResponseEntity.badRequest().headers(headers).body(body);
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    if (findCause(ex, RequestBodyLimitFilter.PayloadTooLargeException.class) != null) {
      return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
          .body(
              problem(
                  HttpStatus.PAYLOAD_TOO_LARGE,
                  "payload-too-large",
                  "Payload too large",
                  "Request body exceeds the allowed size."));
    }
    return ResponseEntity.badRequest()
        .body(
            problem(
                HttpStatus.BAD_REQUEST,
                "malformed-body",
                "Malformed request body",
                "Request body must be valid JSON with the expected fields."));
  }

  /** Adds the legacy {@code error} member to problems produced by the base class. */
  @Override
  protected ResponseEntity<Object> createResponseEntity(
      @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
    if (body instanceof ProblemDetail pd && pd.getDetail() != null) {
      pd.setProperty("error", pd.getDetail());
    }
    return super.createResponseEntity(body, headers, statusCode, request);
  }

  static ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
    pd.setType(URI.create(TYPE_BASE + type));
    pd.setTitle(title);
    pd.setProperty("error", detail);
    return pd;
  }

  private static ProblemDetail withProperty(ProblemDetail pd, String name, Object value) {
    pd.setProperty(name, value);
    return pd;
  }

  private static <T extends Throwable> T findCause(Throwable ex, Class<T> type) {
    for (Throwable t = ex; t != null; t = t.getCause()) {
      if (type.isInstance(t)) {
        return type.cast(t);
      }
    }
    return null;
  }
}
