package io.github.codebyjuliamendes.agentledger.domain.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mandatory rule that classifies destructive or privilege-escalating intents as critical. It cannot
 * be disabled by configuration. Matching is word-based (with common inflections) rather than
 * substring-based because a critical verdict is a hard block that no reviewer can override, so
 * false positives such as "dropdown" must be avoided.
 */
public final class DestructiveActionRule implements PolicyRule {

  public static final String ID = "mandatory-destructive";

  /** Base forms covered by the rule (inflections such as "deleted" also match). */
  public static final List<String> BASE_TERMS =
      List.of("delete", "drop", "remove", "erase", "destroy", "truncate", "wipe", "purge", "admin");

  private static final Pattern DESTRUCTIVE =
      Pattern.compile(
          "\\b(?:"
              + "delet(?:e|es|ed|ing|ion|ions)"
              + "|drop(?:s|ped|ping)?"
              + "|remov(?:e|es|ed|ing|al|als)"
              + "|eras(?:e|es|ed|ing|ure)"
              + "|destroy(?:s|ed|ing)?|destruction"
              + "|truncat(?:e|es|ed|ing|ion)"
              + "|wip(?:e|es|ed|ing)"
              + "|purg(?:e|es|ed|ing)"
              + "|admin(?:s|istrator|istrators)?"
              + ")\\b",
          Pattern.UNICODE_CHARACTER_CLASS);

  @Override
  public String id() {
    return ID;
  }

  @Override
  public Optional<RuleMatch> evaluate(ProposalContent content) {
    Matcher matcher = DESTRUCTIVE.matcher(content.searchableText());
    List<String> hits = new ArrayList<>();
    while (matcher.find()) {
      if (!hits.contains(matcher.group())) {
        hits.add(matcher.group());
      }
    }
    return hits.isEmpty()
        ? Optional.empty()
        : Optional.of(new RuleMatch(ID, RiskLevel.CRITICAL, hits));
  }
}
