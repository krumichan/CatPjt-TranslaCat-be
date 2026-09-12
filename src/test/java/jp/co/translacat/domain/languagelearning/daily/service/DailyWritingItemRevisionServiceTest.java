package jp.co.translacat.domain.languagelearning.daily.service;

import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingDifficulty;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingItem;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DailyWritingItemRevisionServiceTest {

    private final DailyWritingItemRevisionService service =
            new DailyWritingItemRevisionService();

    @Test
    void sameContentProducesSameRevision() {
        DailyWritingItem first = item("한국어 문제");
        DailyWritingItem second = item("한국어 문제");

        assertThat(service.revision(first))
                .isEqualTo(service.revision(second));
    }

    @Test
    void contentChangeProducesDifferentRevision() {
        DailyWritingItem first = item("기존 문제");
        DailyWritingItem second = item("재생성된 문제");

        assertThat(service.revision(first))
                .isNotEqualTo(service.revision(second));
    }

    private DailyWritingItem item(String originText) {
        DailyWritingItem item = mock(DailyWritingItem.class);
        when(item.getOrderNo()).thenReturn(2);
        when(item.getDifficulty()).thenReturn(
                DailyWritingDifficulty.NORMAL
        );
        when(item.getOriginText()).thenReturn(originText);
        when(item.getKeywordsJson()).thenReturn("[]");
        when(item.getFocusMetricsJson()).thenReturn("[]");
        when(item.getFocusReason()).thenReturn("이유");
        when(item.getProvidedFactsJson()).thenReturn("[]");
        when(item.getRequiredIntentsJson()).thenReturn("[]");
        when(item.getResponseConstraintsJson()).thenReturn("[]");
        return item;
    }
}
