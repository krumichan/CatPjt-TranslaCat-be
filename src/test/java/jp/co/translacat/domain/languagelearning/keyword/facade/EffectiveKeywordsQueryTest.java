package jp.co.translacat.domain.languagelearning.keyword.facade;

import jp.co.translacat.domain.languagelearning.common.enums.KeywordSource;
import jp.co.translacat.domain.languagelearning.common.enums.KeywordType;
import jp.co.translacat.domain.languagelearning.keyword.model.KeywordCandidateSnapshot;
import jp.co.translacat.domain.languagelearning.keyword.port.KeywordCatalogGateway;
import jp.co.translacat.domain.languagelearning.setting.dto.response.UserSettingResponseDto;
import jp.co.translacat.domain.languagelearning.setting.port.UserSettingsGateway;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EffectiveKeywordsQueryTest {
    private final KeywordCatalogGateway keywords = mock(KeywordCatalogGateway.class);
    private final UserSettingsGateway settings = mock(UserSettingsGateway.class);
    private final UserSettingResponseDto setting = mock(UserSettingResponseDto.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-15T15:30:00Z"), ZoneOffset.UTC);
    private final LanguageLearningKeywordFacade facade = new LanguageLearningKeywordFacade(keywords, settings, clock);

    @Test
    void currentTimezoneDeterminesDateAndOnlyEffectiveCandidatesAreReturned() {
        // 준비: UTC 날짜와 도쿄 학습일이 다른 시간에 현재 적용 후보만 준비한다.
        var date = LocalDate.of(2026, 10, 16);
        var candidate = new KeywordCandidateSnapshot(
                "CUSTOM:12", "요건정의", KeywordSource.CUSTOM, KeywordType.TOPIC, null, date
        );
        when(settings.get(123L)).thenReturn(setting);
        when(setting.timezone()).thenReturn("Asia/Tokyo");
        when(keywords.candidates(123L, date)).thenReturn(List.of(candidate));

        // 실행
        var response = facade.getEffectiveKeywords(123L);

        // 검증: 편집 목록과 클라이언트 로케일에 의존하지 않는다.
        assertEquals(List.of(candidate), response.candidates());
        verify(keywords).candidates(123L, date);
        verify(keywords, never()).getKeywords(anyLong(), anyString());
        verifyNoMoreInteractions(keywords);
    }

    @Test
    void pendingTimezoneDoesNotChangeTodaysEffectiveSelection() {
        // 준비: 내일 시간대 변경을 예약했더라도 현재 활성 시간대를 사용한다.
        var date = LocalDate.of(2026, 10, 15);
        when(settings.get(123L)).thenReturn(setting);
        when(setting.timezone()).thenReturn("Pacific/Honolulu");
        when(setting.pendingTimezone()).thenReturn("Asia/Tokyo");
        when(keywords.candidates(123L, date)).thenReturn(List.of());

        // 실행
        var response = facade.getEffectiveKeywords(123L);

        // 검증: 빈 적용 목록도 원본 그대로 반환하며 희망 상태로 보완하지 않는다.
        assertTrue(response.candidates().isEmpty());
        verify(keywords).candidates(123L, date);
        verify(setting, never()).pendingTimezone();
    }

    @Test
    void invalidStoredTimezoneUsesTheSameTokyoDefaultAsLl() {
        // 준비: LL의 기존 저장 시간대 보정 규칙과 같은 날짜를 계산한다.
        var date = LocalDate.of(2026, 10, 16);
        when(settings.get(123L)).thenReturn(setting);
        when(setting.timezone()).thenReturn("invalid-timezone");
        when(keywords.candidates(123L, date)).thenReturn(List.of());

        // 실행
        facade.getEffectiveKeywords(123L);

        // 검증
        verify(keywords).candidates(123L, date);
    }

    @Test
    void unavailableSettingsAreNotHiddenByAClientDateOrEmptyFallback() {
        // 준비: 활성 시간대를 확인할 수 없으면 후보 요청도 진행하지 않는다.
        var failure = new IllegalStateException("settings unavailable");
        when(settings.get(123L)).thenThrow(failure);

        // 실행 및 검증
        assertSame(failure, assertThrows(IllegalStateException.class, () -> facade.getEffectiveKeywords(123L)));
        verifyNoInteractions(keywords);
    }
}
