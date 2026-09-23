package jp.co.translacat.domain.accountbook.receiptkeyword.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptCategorySuggestionPolicyTest {
    @Test
    void canonicalizesExistingAndDefaultNamesWithoutInventingDuplicates() {
        assertThat(ReceiptCategorySuggestionPolicy.resolve(
                "food", "matched items", List.of("Food"), List.of("식비", "기타")))
                .satisfies(result -> {
                    assertThat(result.name()).isEqualTo("Food");
                    assertThat(result.source()).isEqualTo("EXISTING");
                    assertThat(result.warnings()).isEmpty();
                });
        assertThat(ReceiptCategorySuggestionPolicy.resolve(
                "식비", "prepared food", List.of("Food"), List.of("식비", "기타")))
                .satisfies(result -> {
                    assertThat(result.name()).isEqualTo("식비");
                    assertThat(result.source()).isEqualTo("DEFAULT");
                });
    }

    @Test
    void preservesSafeNewSuggestionButFallsBackForMissingOrUnsafeNames() {
        assertThat(ReceiptCategorySuggestionPolicy.resolve(
                "반려동물", "pet supplies", List.of("Food"), List.of("식비", "기타")))
                .satisfies(result -> {
                    assertThat(result.name()).isEqualTo("반려동물");
                    assertThat(result.source()).isEqualTo("NEW");
                    assertThat(result.warnings()).containsExactly("CATEGORY_NEW_SUGGESTION");
                });
        for (String unsafe : new String[] {null, "   ", "bad\nname", "---"}) {
            assertThat(ReceiptCategorySuggestionPolicy.resolve(
                    unsafe, null, List.of("Food"), List.of("식비", "기타")))
                    .satisfies(result -> {
                        assertThat(result.name()).isEqualTo("기타");
                        assertThat(result.source()).isEqualTo("FALLBACK");
                        assertThat(result.warnings()).containsExactly("CATEGORY_FALLBACK_USED");
                    });
        }
    }
}
