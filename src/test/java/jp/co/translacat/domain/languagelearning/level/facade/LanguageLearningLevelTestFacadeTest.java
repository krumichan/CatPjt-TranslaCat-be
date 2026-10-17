package jp.co.translacat.domain.languagelearning.level.facade;

import jp.co.translacat.domain.languagelearning.level.dto.request.LevelAnswerRequestDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelAnswerResultResponseDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelStatusResponseDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelAudioAnswerResultResponseDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelTestResultResponseDto;
import jp.co.translacat.domain.languagelearning.common.enums.LearningProfileState;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionType;
import jp.co.translacat.domain.languagelearning.growth.model.GrowthSnapshot;
import jp.co.translacat.domain.languagelearning.growth.port.GrowthReadGateway;
import jp.co.translacat.domain.languagelearning.level.port.LevelTestGateway;
import jp.co.translacat.domain.languagelearning.profile.service.LevelTestBaselineBridge;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class LanguageLearningLevelTestFacadeTest {
    @Test
    void completedOldLanguageAnswerDoesNotRequireTheNewLanguageBaseline() {
        // 준비: LL은 이전 일본어 세션의 완료를 확정했고 현재 영어 기준점은 아직 없다.
        var g = mock(LevelTestGateway.class);
        var growth = mock(GrowthReadGateway.class);
        var b = new LevelTestBaselineBridge(growth);
        var f = new LanguageLearningLevelTestFacade(g, b);
        var request = new LevelAnswerRequestDto(null, List.of(), "回答", "key");
        var result = new LevelAnswerResultResponseDto(1L, 20L, 20, true, 80, null, true, null);
        when(g.submit(123L, 1L, 20L, request)).thenReturn(result);
        when(growth.snapshot(123L)).thenReturn(new GrowthSnapshot(123, null, List.of(), Map.of()));

        // 실행
        assertSame(result, f.submit(123L, 1L, 20L, request));

        // 검증: 현재 영어가 미측정이어도 소유 세션의 완료를 거부하거나 영어 기준점을 만들지 않는다.
        verifyNoInteractions(growth);
        assertNull(b.synchronize(123L));
    }

    @Test
    void nonFinalAnswerDoesNotApplyProfile() {
        var g = mock(LevelTestGateway.class);
        var b = mock(LevelTestBaselineBridge.class);
        var f = new LanguageLearningLevelTestFacade(g, b);
        var request = new LevelAnswerRequestDto("A", List.of(), null, "key");
        when(g.submit(123L, 1L, 1L, request)).thenReturn(
                new LevelAnswerResultResponseDto(1L, 1L, 2, true, 100, null, false, null));
        f.submit(123L, 1L, 1L, request);
        verifyNoInteractions(b);
    }

    @Test
    void firstStatusReadPromotesLanguageBeforeReadingTheCurrentBaseline() {
        // 준비: LL 상태 조회가 예약된 영어를 적용하며 이전 일본어 ACTIVE 상태를 사용할 수 없게 한다.
        var gateway = mock(LevelTestGateway.class);
        var baseline = mock(LevelTestBaselineBridge.class);
        var promoted = new boolean[] { false };
        when(gateway.status(123L)).thenAnswer(call -> {
            promoted[0] = true;
            return new LevelStatusResponseDto(LearningProfileState.LEVEL_TEST_REQUIRED, false, false,
                    null, null, null, null);
        });
        when(baseline.synchronize(123L)).thenAnswer(call -> promoted[0] ? null : LearningProfileState.ACTIVE);

        // 실행
        var result = new LanguageLearningLevelTestFacade(gateway, baseline).getStatus(123L);

        // 검증: 새 언어의 INITIAL 필요 상태를 이전 언어 성장 상태로 덮어쓰지 않는다.
        assertEquals(LearningProfileState.LEVEL_TEST_REQUIRED, result.profileState());
        var ordered = inOrder(gateway, baseline);
        ordered.verify(gateway).status(123L);
        ordered.verify(baseline).synchronize(123L);
    }

    @Test
    void explicitOldLanguageResultRemainsReadableWhileCurrentEnglishStillRequiresInitialTest() {
        // 준비: LL의 기존 결과 endpoint는 요청 사용자의 COMPLETED 세션만 반환한다.
        var gateway = mock(LevelTestGateway.class);
        var growth = mock(GrowthReadGateway.class);
        var baseline = new LevelTestBaselineBridge(growth);
        var facade = new LanguageLearningLevelTestFacade(gateway, baseline);
        var result = new LevelTestResultResponseDto(1L, LevelTestSessionType.INITIAL, 80, "ADVANCED", null,
                "MY_LEVEL", LocalDateTime.parse("2026-10-16T01:00:00"));
        when(gateway.result(123L, 1L)).thenReturn(result);
        when(gateway.status(123L)).thenReturn(new LevelStatusResponseDto(LearningProfileState.LEVEL_TEST_REQUIRED,
                false, false, null, null, null, null));
        when(growth.snapshot(123L)).thenReturn(new GrowthSnapshot(123, null, List.of(), Map.of()));

        // 실행
        var historical = facade.getResult(123L, 1L);
        var current = facade.getStatus(123L);

        // 검증: 이전 ID의 결과와 현재 언어의 미측정 상태를 함께 보존한다.
        assertSame(result, historical);
        assertEquals(LearningProfileState.LEVEL_TEST_REQUIRED, current.profileState());
        assertNull(baseline.synchronize(123L));
        verify(growth, times(2)).snapshot(123L);
        verifyNoMoreInteractions(growth);
    }

    @Test
    void completedRetryAndAudioUseTheOwnedSessionCompletionWithoutReadingCurrentGrowth() {
        // 준비: 두 완료 경로도 언어쌍을 가진 원본 세션은 LL이 소유한다.
        var gateway = mock(LevelTestGateway.class);
        var baseline = mock(LevelTestBaselineBridge.class);
        var facade = new LanguageLearningLevelTestFacade(gateway, baseline);
        var retry = new LevelAnswerResultResponseDto(1L, 20L, 20, true, 80, null, true, null);
        var audio = new LevelAudioAnswerResultResponseDto(1L, 20L, true, 80, null, true, null, null);
        when(gateway.retry(123L, 1L, 20L)).thenReturn(retry);
        when(gateway.submitAudio(eq(123L), eq(1L), eq(20L), any(byte[].class), eq("audio/wav"), eq(1000), eq("audio-key")))
                .thenReturn(audio);
        var upload = new MockMultipartFile("audio", "synthetic.wav", "audio/wav", new byte[] { 1, 2 });

        // 실행
        assertSame(retry, facade.retryEvaluation(123L, 1L, 20L));
        assertSame(audio, facade.submitAudio(123L, 1L, 20L, upload, 1000, "audio-key"));

        // 검증
        verifyNoInteractions(baseline);
    }

    @Test
    void originalSessionOwnershipOrCompletionFailureIsStillPropagated() {
        // 준비: 현재 성장 자료를 이용해 LL의 소유권/완료 검증 실패를 우회하지 않는다.
        var gateway = mock(LevelTestGateway.class);
        var baseline = mock(LevelTestBaselineBridge.class);
        var failure = new IllegalStateException("LL session is not owned or completed");
        when(gateway.result(123L, 1L)).thenThrow(failure);

        // 실행 / 검증
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> new LanguageLearningLevelTestFacade(gateway, baseline).getResult(123L, 1L)));
        verifyNoInteractions(baseline);
    }
}
