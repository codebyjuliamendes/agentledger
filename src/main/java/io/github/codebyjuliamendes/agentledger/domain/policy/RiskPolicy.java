package io.github.codebyjuliamendes.agentledger.domain.policy;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Configurable term lists. Instances are always normalized and valid. */
public record RiskPolicy(List<String> mediumTerms, List<String> highTerms) {

  public static final int MAX_TERMS = 30;
  public static final int MIN_TERM_LENGTH = 2;
  public static final int MAX_TERM_LENGTH = 40;

  public static final RiskPolicy DEFAULT =
      new RiskPolicy(
          List.of("refund", "payment"),
          List.of("export", "email", "permission", "role", "update", "write", "send"));

  static final String INVALID_MESSAGE =
      "Provide arrays of up to %d risk terms, each %d–%d characters."
          .formatted(MAX_TERMS, MIN_TERM_LENGTH, MAX_TERM_LENGTH);

  public RiskPolicy {
    mediumTerms = normalize(mediumTerms);
    highTerms = normalize(highTerms);
  }

  /** Trims, lower-cases and de-duplicates (keeping first occurrence order). */
  private static List<String> normalize(Collection<String> terms) {
    if (terms == null || terms.size() > MAX_TERMS) {
      throw new InvalidPolicyException(INVALID_MESSAGE);
    }
    LinkedHashSet<String> unique = new LinkedHashSet<>();
    for (String raw : terms) {
      if (raw == null) {
        throw new InvalidPolicyException(INVALID_MESSAGE);
      }
      String term = raw.strip().toLowerCase(Locale.ROOT);
      if (term.length() < MIN_TERM_LENGTH || term.length() > MAX_TERM_LENGTH) {
        throw new InvalidPolicyException(INVALID_MESSAGE);
      }
      unique.add(term);
    }
    return List.copyOf(unique);
  }
}
