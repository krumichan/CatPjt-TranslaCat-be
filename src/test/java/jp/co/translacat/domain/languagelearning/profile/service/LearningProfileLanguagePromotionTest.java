package jp.co.translacat.domain.languagelearning.profile.service;

import jp.co.translacat.domain.languagelearning.common.enums.LearningProfileState;
import jp.co.translacat.domain.languagelearning.growth.model.GrowthProfileSnapshot;
import jp.co.translacat.domain.languagelearning.growth.model.GrowthSnapshot;
import jp.co.translacat.domain.languagelearning.growth.port.GrowthReadGateway;
import jp.co.translacat.domain.languagelearning.practice.service.VocabularyMasteryQueryService;
import jp.co.translacat.domain.languagelearning.setting.port.UserSettingsGateway;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LearningProfileLanguagePromotionTest {
    @Test
    void dueLanguageIsPromotedBeforeReadingGrowthAndVocabulary() {
        // 준비: 설정 조회가 이루어지기 전에는 과거 일본어 프로필만 보이는 경계를 모사한다.
        var growth = mock(GrowthReadGateway.class);
        var vocabulary = mock(VocabularyMasteryQueryService.class);
        var settings = mock(UserSettingsGateway.class);
        var active = new boolean[] { false };
        when(settings.get(123L)).thenAnswer(call -> { active[0] = true; return null; });
        var profile = mock(GrowthProfileSnapshot.class);
        when(profile.state()).thenReturn(LearningProfileState.ACTIVE);
        when(growth.snapshot(123L)).thenAnswer(call -> {
            assertTrue(active[0], "현재 언어가 확정되기 전에 성장 자료를 읽으면 안 됩니다.");
            return new GrowthSnapshot(123, profile, List.of(), Map.of());
        });
        var service = new LearningProfileQueryService(growth, vocabulary, settings);

        // 실행
        service.getProfile(123L);

        // 검증: 실제 조회 순서를 유지해 한 DTO 안의 두 언어 자료가 섞이지 않게 한다.
        var ordered = inOrder(settings, growth, vocabulary);
        ordered.verify(settings).get(123L);
        ordered.verify(growth).snapshot(123L);
        ordered.verify(vocabulary).get(123L);
    }

    @Test
    void newLanguageWithoutBaselineReturnsRequiredStateWithoutBorrowingOldProfile() {
        // 준비: 새 영어 설정 적용 후 해당 언어의 성장 기준점은 아직 없다.
        var growth = mock(GrowthReadGateway.class);
        var vocabulary = mock(VocabularyMasteryQueryService.class);
        var settings = mock(UserSettingsGateway.class);
        when(growth.snapshot(123L)).thenReturn(new GrowthSnapshot(123, null, List.of(), Map.of()));

        // 실행
        var profile = new LearningProfileQueryService(growth, vocabulary, settings).getProfile(123L);

        // 검증
        assertEquals(LearningProfileState.LEVEL_TEST_REQUIRED, profile.state());
        var ordered = inOrder(settings, growth);
        ordered.verify(settings).get(123L);
        ordered.verify(growth).snapshot(123L);
        verifyNoInteractions(vocabulary);
    }

    @Test
    void failedSettingPromotionDoesNotReturnAnyOldLanguageGrowth() {
        // 준비
        var growth = mock(GrowthReadGateway.class);
        var vocabulary = mock(VocabularyMasteryQueryService.class);
        var settings = mock(UserSettingsGateway.class);
        when(settings.get(123L)).thenThrow(new IllegalStateException("settings unavailable"));

        // 실행 / 검증: 설정 실패를 과거 성장 자료로 감추지 않는다.
        assertThrows(IllegalStateException.class,
                () -> new LearningProfileQueryService(growth, vocabulary, settings).getProfile(123L));
        verifyNoInteractions(growth, vocabulary);
    }
}
