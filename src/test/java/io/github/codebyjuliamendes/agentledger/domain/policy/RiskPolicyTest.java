package io.github.codebyjuliamendes.agentledger.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RiskPolicyTest {

  @Test
  void normalizesTrimsLowercasesAndDeduplicates() {
    RiskPolicy policy =
        new RiskPolicy(List.of(" Refund ", "refund", "PAYMENT"), List.of("Export", "export "));

    assertThat(policy.mediumTerms()).containsExactly("refund", "payment");
    assertThat(policy.highTerms()).containsExactly("export");
  }

  @Test
  void allowsEmptyLists() {
    assertThat(new RiskPolicy(List.of(), List.of()).mediumTerms()).isEmpty();
  }

  @Test
  void rejectsTooShortOrTooLongTerms() {
    assertThatThrownBy(() -> new RiskPolicy(List.of(" a "), List.of()))
        .isInstanceOf(InvalidPolicyException.class)
        .hasMessageContaining("2–40");
    assertThatThrownBy(() -> new RiskPolicy(List.of(), List.of("x".repeat(41))))
        .isInstanceOf(InvalidPolicyException.class);
  }

  @Test
  void rejectsTooManyTerms() {
    List<String> terms = IntStream.range(0, 31).mapToObj(i -> "term" + i).toList();
    assertThatThrownBy(() -> new RiskPolicy(terms, List.of()))
        .isInstanceOf(InvalidPolicyException.class);
  }

  @Test
  void rejectsNullListsAndNullTerms() {
    assertThatThrownBy(() -> new RiskPolicy(null, List.of()))
        .isInstanceOf(InvalidPolicyException.class);
    List<String> withNull = new ArrayList<>(Arrays.asList("ok", null));
    assertThatThrownBy(() -> new RiskPolicy(withNull, Collections.emptyList()))
        .isInstanceOf(InvalidPolicyException.class);
  }

  @Test
  void listsAreImmutable() {
    RiskPolicy policy = new RiskPolicy(new ArrayList<>(List.of("refund")), List.of());
    assertThatThrownBy(() -> policy.mediumTerms().add("x"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void riskLevelCodesRoundTrip() {
    for (RiskLevel level : RiskLevel.values()) {
      assertThat(RiskLevel.fromCode(level.code())).isEqualTo(level);
    }
    assertThat(RiskLevel.HIGH.isAbove(RiskLevel.MEDIUM)).isTrue();
  }
}
