package io.github.codebyjuliamendes.agentledger.web.dto;

import io.github.codebyjuliamendes.agentledger.domain.audit.ChainVerification;

/**
 * @param headHash hash of the newest entry; publish it elsewhere to anchor the chain
 * @param brokenAt sequence number of the first bad entry, when invalid
 */
public record VerificationResponse(
    boolean valid, long checked, String headHash, Long brokenAt, String reason) {

  public static VerificationResponse from(ChainVerification v) {
    return switch (v) {
      case ChainVerification.Intact(long checked, String head) ->
          new VerificationResponse(true, checked, head, null, null);
      case ChainVerification.Broken(long checked, long seq, String reason) ->
          new VerificationResponse(false, checked, null, seq, reason);
    };
  }
}
