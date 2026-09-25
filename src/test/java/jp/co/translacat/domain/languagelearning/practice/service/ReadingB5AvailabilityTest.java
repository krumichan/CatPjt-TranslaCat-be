package jp.co.translacat.domain.languagelearning.practice.service;

import jp.co.translacat.domain.languagelearning.ai.dto.model.ReadingPassageBundleDto;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiPracticeGenerationRequestDto;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain;
import jp.co.translacat.domain.languagelearning.practice.policy.PracticeAvailabilityPolicy;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ReadingB5AvailabilityTest {
    static AiPracticeGenerationRequestDto request(int band, String mode) {
        return new AiPracticeGenerationRequestDto("preview", PracticeDomain.READING, mode,
                "ko", "ja", 5, band, 1, 3, 1, List.of(), List.of(), List.of(), List.of(),
                0, LocalDate.of(2026, 9, 21), List.of());
    }

    @Test
    void fiveServerSlotsAndAllModesHaveExactB5Boundary() {
        for (int band = 1; band <= 5; band++) {
            for (String mode : List.of("COMPREHENSION", "STRUCTURE", "CONTEXT_INFERENCE")) {
                var request = request(band, mode);
                var targets = PracticePersistenceService.readingSlotTargets(request);
                assertThat(targets).extracting(target -> target.complexityBand())
                        .containsExactly(band, Math.max(1, band - 1), band,
                                Math.min(5, band + 1), band);
                assertThat(PracticeAvailabilityPolicy.needsAnyNewB5Structure(request, targets, 1))
                        .isEqualTo("STRUCTURE".equals(mode) && band >= 4);
            }
        }
    }

    @Test
    void challengeInSecondPassageBlocksBeforeFirstProviderCall() {
        var request = request(4, "STRUCTURE");
        var targets = PracticePersistenceService.readingSlotTargets(request);
        assertThat(PracticeAvailabilityPolicy.needsNewB5Structure(request, targets, 1)).isFalse();
        assertThat(PracticeAvailabilityPolicy.needsNewB5Structure(request, targets, 4)).isTrue();
        assertThat(PracticeAvailabilityPolicy.needsAnyNewB5Structure(request, targets, 1)).isTrue();
    }

    @Test
    void privatelyVerifiedBundleCanStillPublishWithoutNewB5Generation() {
        var original = request(4, "STRUCTURE");
        var persisted = new AiPracticeGenerationRequestDto(
                original.requestId(), original.domain(), original.mode(), original.originLanguage(),
                original.learningLanguage(), original.questionCount(), original.complexityBand(),
                original.easierCount(), original.currentCount(), original.challengeCount(),
                original.selectedKeywords(), original.weakSignals(), original.recentMistakes(),
                original.reviewTargets(), original.reviewQuestionCount(), original.generationDate(),
                original.previousQuestions(), null, false,
                Map.of("p2", mock(ReadingPassageBundleDto.class)));
        assertThat(PracticeAvailabilityPolicy.needsAnyNewB5Structure(
                persisted, PracticePersistenceService.readingSlotTargets(persisted), 1)).isFalse();
    }
}
