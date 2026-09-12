package jp.co.translacat.domain.languagelearning.daily.service;

import jp.co.translacat.domain.languagelearning.ai.dto.model.DailyWritingGeneratedItemDto;
import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingDifficulty;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingItem;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingSet;
import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingItemRepository;
import jp.co.translacat.domain.languagelearning.quality.service.GenerationFingerprintCommandService;
import jp.co.translacat.domain.user.entity.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyWritingItemCommandServiceRegenerationTest {

    @Mock private DailyWritingItemRepository itemRepository;
    @Mock private LanguageLearningJsonCodec jsonCodec;
    @Mock private GenerationFingerprintCommandService fingerprintCommandService;
    @Mock private DailyWritingItem normalItem;
    @Mock private DailyWritingItem reviewItem;
    @Mock private DailyWritingSet dailySet;
    @Mock private User user;

    private DailyWritingItemCommandService service;

    @BeforeEach
    void setUp() {
        service = new DailyWritingItemCommandService(
                itemRepository,
                jsonCodec,
                fingerprintCommandService
        );
        when(jsonCodec.write(any())).thenReturn("[]");
        when(normalItem.getOrderNo()).thenReturn(2);
        when(normalItem.getDifficulty()).thenReturn(
                DailyWritingDifficulty.NORMAL
        );
        when(normalItem.getDailySet()).thenReturn(dailySet);
        when(normalItem.getId()).thenReturn(102L);
        when(reviewItem.getOrderNo()).thenReturn(5);
        when(reviewItem.getDifficulty()).thenReturn(
                DailyWritingDifficulty.REVIEW
        );
        when(reviewItem.getDailySet()).thenReturn(dailySet);
        when(reviewItem.getId()).thenReturn(105L);
        when(dailySet.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(10L);
    }

    @Test
    void regeneratedItemsArePairedByDifficultyNotLocalAiOrder() {
        DailyWritingGeneratedItemDto reviewGenerated =
                new DailyWritingGeneratedItemDto(
                        1,
                        DailyWritingDifficulty.REVIEW,
                        "review-new",
                        List.of(),
                        List.of(),
                        "review-reason"
                );
        DailyWritingGeneratedItemDto normalGenerated =
                new DailyWritingGeneratedItemDto(
                        2,
                        DailyWritingDifficulty.NORMAL,
                        "normal-new",
                        List.of(),
                        List.of(),
                        "normal-reason"
                );

        service.replaceAll(
                "ja",
                List.of(normalItem, reviewItem),
                List.of(reviewGenerated, normalGenerated)
        );

        verify(normalItem).replace(
                eq(DailyWritingDifficulty.NORMAL),
                eq("normal-new"),
                anyString(),
                anyString(),
                eq("normal-reason"),
                anyString(),
                anyString(),
                anyString()
        );
        verify(reviewItem).replace(
                eq(DailyWritingDifficulty.REVIEW),
                eq("review-new"),
                anyString(),
                anyString(),
                eq("review-reason"),
                anyString(),
                anyString(),
                anyString()
        );
    }
}
