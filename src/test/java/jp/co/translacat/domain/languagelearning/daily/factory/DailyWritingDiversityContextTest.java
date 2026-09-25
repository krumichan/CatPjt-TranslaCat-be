package jp.co.translacat.domain.languagelearning.daily.factory;

import jp.co.translacat.domain.languagelearning.ai.dto.model.DifficultyDistributionDto;
import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingType;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingItem;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingSet;
import jp.co.translacat.domain.languagelearning.daily.model.DailyWritingSnapshot;
import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingItemRepository;
import jp.co.translacat.domain.languagelearning.quality.common.LanguageLearningContentSource;
import jp.co.translacat.domain.languagelearning.quality.dto.DiversityContext;
import jp.co.translacat.domain.languagelearning.quality.dto.DiversityHistoryItem;
import jp.co.translacat.domain.languagelearning.quality.policy.LanguageComplexityPolicy;
import jp.co.translacat.domain.languagelearning.quality.service.GenerationDiversityContextService;
import jp.co.translacat.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 새 문제의 정원과 과거 중복 방지 문맥을 분리한다. 사용자 선택 정책은 바꾸지 않는다.
 */
class DailyWritingDiversityContextTest {
    private final GenerationDiversityContextService diversity = mock(GenerationDiversityContextService.class);
    private final DailyWritingItemRepository items = mock(DailyWritingItemRepository.class);
    private final DailyWritingGenerationRequestFactory factory =
            new DailyWritingGenerationRequestFactory(diversity, new LanguageComplexityPolicy(), items);
    private final DailyWritingSet set = mock(DailyWritingSet.class);
    private final DailyWritingSnapshot snapshot =
            new DailyWritingSnapshot("ko", "ja", 5, new DifficultyDistributionDto(1, 3, 1), List.of(), null, null,
                    List.of(), List.of(), LocalDate.of(2026, 9, 25), "stable-snapshot");
    private final DiversityHistoryItem old =
            new DiversityHistoryItem(LanguageLearningContentSource.WRITING, "지난 회의 자료", "old-hash", "WORK", "REPORT",
                    "PAST_REPORT", List.of("PAST"), "과거 업무 보고", 0);
    private Map<String, String> suppliedContents;

    @BeforeEach
    void setup() {
        when(set.getId()).thenReturn(20L);
        when(set.getWritingType()).thenReturn(DailyWritingType.TRANSLATION);
        when(diversity.contextForSources(eq(10L), eq("ja"), eq(LanguageLearningContentSource.WRITING),
                anyMap())).thenAnswer(invocation -> {
            suppliedContents = new LinkedHashMap<>(invocation.<Map<String, String>>getArgument(3));
            return new DiversityContext(List.of(), List.of(old), List.of(), List.of("old-hash"));
        });
    }

    @Test
    void progressiveGenerationKeepsEveryPublishedItemInOrder() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(10L);
        when(set.getUser()).thenReturn(user);

        DailyWritingItem item31 = item(31L);
        DailyWritingItem item32 = item(32L);

        when(items.findAllByDailySetIdOrderByOrderNoAsc(20L)).thenReturn(List.of(item31, item32));

        var request = factory.createItem(set, snapshot, 3, "lease");

        assertThat(suppliedContents.keySet()).containsExactly("31", "32");
        assertThat(request.sentenceCount()).isEqualTo(1);
        assertThat(request.difficultyDistribution()).isEqualTo(new DifficultyDistributionDto(0, 1, 0));
        assertThat(request.selectedKeywords()).isEmpty();
        assertThat(request.snapshotId()).isEqualTo("stable-snapshot");
    }

    @Test
    void regenerationCountsOnlyRetainedItemsButKeepsHistoricalDuplicates() {
        DailyWritingItem item31 = item(31L);
        DailyWritingItem item32 = item(32L);
        DailyWritingItem item33 = item(33L);
        DailyWritingItem item34 = item(34L);
        DailyWritingItem item35 = item(35L);

        when(items.findAllByDailySetIdOrderByOrderNoAsc(20L)).thenReturn(
                List.of(item31, item32, item33, item34, item35));

        var request = factory.createRegeneration(10L, set, snapshot, 3, new DifficultyDistributionDto(0, 2, 1), "regen",
                Set.of(32L, 34L, 35L));

        assertThat(suppliedContents.keySet()).containsExactly("31", "33");
        assertThat(request.diversityContext().sameFeatureRecent()).containsExactly(old);
        assertThat(request.diversityContext().exactContentHashes90d()).containsExactly("old-hash");
        assertThat(request.difficultyDistribution()).isEqualTo(new DifficultyDistributionDto(0, 2, 1));
        assertThat(request.sentenceCount()).isEqualTo(3);
        assertThat(request.selectedKeywords()).isEmpty();
        verify(items, never()).delete(any(DailyWritingItem.class));
    }

    @Test
    void replacingTheWholeSetHasNoRetainedQuotaItems() {
        DailyWritingItem item31 = item(31L);
        DailyWritingItem item32 = item(32L);

        when(items.findAllByDailySetIdOrderByOrderNoAsc(20L)).thenReturn(List.of(item31, item32));

        var request = factory.createRegeneration(10L, set, snapshot, 2, new DifficultyDistributionDto(1, 1, 0), "regen",
                Set.of(31L, 32L));

        assertThat(suppliedContents).isEmpty();
        assertThat(request.diversityContext().exactContentHashes90d()).containsExactly("old-hash");
    }

    @Test
    void initialGenerationDoesNotInventDefaultKeywords() {
        when(items.findAllByDailySetIdOrderByOrderNoAsc(20L)).thenReturn(List.of());
        var request = factory.createInitial(10L, set, snapshot);
        assertThat(request.selectedKeywords()).isEmpty();
        assertThat(suppliedContents).isEmpty();
        assertThat(request.difficultyDistribution()).isEqualTo(snapshot.difficultyDistribution());
    }

    private DailyWritingItem item(long id) {
        DailyWritingItem item = mock(DailyWritingItem.class);
        when(item.getId()).thenReturn(id);
        // 교체 대상의 본문은 접근하지 않으므로 lenient를 사용한다.
        lenient().when(item.getOriginText()).thenReturn("기존 문제 " + id);
        return item;
    }
}
