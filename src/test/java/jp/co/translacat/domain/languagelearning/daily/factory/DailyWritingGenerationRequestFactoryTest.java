package jp.co.translacat.domain.languagelearning.daily.factory;

import jp.co.translacat.domain.languagelearning.ai.dto.model.DifficultyDistributionDto;
import jp.co.translacat.domain.languagelearning.daily.model.DailyWritingSnapshot;
import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingItemRepository;
import jp.co.translacat.domain.languagelearning.quality.policy.LanguageComplexityPolicy;
import jp.co.translacat.domain.languagelearning.quality.service.GenerationDiversityContextService;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class DailyWritingGenerationRequestFactoryTest {

    private final DailyWritingGenerationRequestFactory factory = new DailyWritingGenerationRequestFactory(
            mock(GenerationDiversityContextService.class), mock(LanguageComplexityPolicy.class),
            mock(DailyWritingItemRepository.class));

    @Test
    void eachSlotRetainsWholeSetDifficultyDistribution() {
        DailyWritingSnapshot snapshot = snapshot();
        assertThat(factory.distributionForItem(snapshot, 1)).isEqualTo(new DifficultyDistributionDto(1, 0, 0));
        for (int order = 2; order <= 4; order++) {
            assertThat(factory.distributionForItem(snapshot, order)).isEqualTo(new DifficultyDistributionDto(0, 1, 0));
        }
        assertThat(factory.distributionForItem(snapshot, 5)).isEqualTo(new DifficultyDistributionDto(0, 0, 1));
    }

    @Test
    void invalidSlotCannotSilentlyBecomeChallenge() {
        assertThatThrownBy(() -> factory.distributionForItem(snapshot(), 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> factory.distributionForItem(snapshot(), 6)).isInstanceOf(IllegalArgumentException.class);
    }

    private DailyWritingSnapshot snapshot() {
        return new DailyWritingSnapshot("ko", "ja", 5, new DifficultyDistributionDto(1, 3, 1),
                List.of(), null, null, List.of(), List.of(), LocalDate.now(), "snapshot");
    }
}
