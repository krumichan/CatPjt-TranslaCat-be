package jp.co.translacat.domain.voice;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.voice.entity.VoiceSegment;
import jp.co.translacat.domain.voice.entity.VoiceSession;
import jp.co.translacat.domain.voice.enums.*;
import jp.co.translacat.domain.voice.model.VoiceStreamContext;
import jp.co.translacat.domain.voice.repository.*;
import jp.co.translacat.domain.voice.service.*;
import jp.co.translacat.domain.voice.support.VoiceErrorCode;
import jp.co.translacat.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class VoiceLanguageRetryTest {
    private final VoiceSegmentRepository segments = mock(VoiceSegmentRepository.class);
    private final VoiceTranslationRetryQueryService retry = new VoiceTranslationRetryQueryService(segments);

    @ParameterizedTest
    @ValueSource(strings = {"VOICE_AI_LANGUAGE_MISMATCH", "VOICE_AI_UNSUPPORTED_LANGUAGE", "VOICE_AI_LANGUAGE_UNDETERMINED"})
    void languageRejectionIsPreservedAndCannotBeRetried(String code) {
        // 준비: 원문은 저장됐으나 언어 필터가 번역을 거절한 경우다.
        var session = mock(VoiceSession.class);
        when(session.getStatus()).thenReturn(VoiceSessionStatus.ACTIVE);
        var sessions = mock(VoiceSessionRepository.class);
        when(sessions.findOwnedForUpdate("voice", 1L)).thenReturn(Optional.of(session));
        var segment = mock(VoiceSegment.class);
        when(segments.findBySession_IdAndChannelAndUtteranceSequence("voice", VoiceChannel.REMOTE, 1))
                .thenReturn(Optional.of(segment));
        var mapper = new ObjectMapper();
        var finals = new VoiceFinalResultCommandService(sessions, segments, mock(VoiceUsageLedgerRepository.class),
                mock(VoiceStreamCommandService.class), mapper);
        var event = mapper.createObjectNode();
        event.put("sourceText", "A rejected utterance");
        event.putObject("error").put("stage", "LANGUAGE").put("code", code);
        var context = new VoiceStreamContext(1L, "voice", VoiceChannel.REMOTE, "connection", VoiceMode.MEDIA,
                VoiceSourceLanguageMode.AUTO, null, "en", "ko", "{}", 0);

        // 실행: LANGUAGE 실패도 이력 상태에 저장한다.
        finals.persistTranslationFailure(context, event, 1);

        // 검증: 이력을 다시 열어도 언어 거절을 일반 번역 재시도로 우회할 수 없다.
        verify(segment).markTranslationFailed(code);
        when(segment.getStatus()).thenReturn(VoiceSegmentStatus.TRANSLATION_FAILED);
        when(segment.getErrorCode()).thenReturn(code);
        when(segments.findByIdAndSession_IdAndSession_User_Id(2L, "voice", 1L)).thenReturn(Optional.of(segment));
        assertThatThrownBy(() -> retry.prepare(1L, "voice", 2L))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(VoiceErrorCode.RETRY_NOT_ALLOWED));
    }

    @Test
    void autoLanguageSwitchRetryUsesConfidentDetectedLanguageBeforeOldLock() {
        // 준비: 채널 대표 언어는 영어지만 이번 문장은 일본어로 확실하게 감지됐다.
        var segment = retryableSegment();
        when(segment.getDetectedLanguage()).thenReturn("ja");
        when(segment.getLanguageConfidence()).thenReturn(0.9);
        when(segment.getLockedLanguage()).thenReturn("en");

        // 실행
        var context = retry.prepare(1L, "voice", 2L);

        // 검증
        assertThat(context.sourceLanguage()).isEqualTo("ja");
    }

    @Test
    void uncertainDetectionFallsBackToSupportedLock() {
        // 준비
        var segment = retryableSegment();
        when(segment.getDetectedLanguage()).thenReturn("ja");
        when(segment.getLanguageConfidence()).thenReturn(0.49);
        when(segment.getLockedLanguage()).thenReturn("en");

        // 실행 및 검증
        assertThat(retry.prepare(1L, "voice", 2L).sourceLanguage()).isEqualTo("en");
    }

    @Test
    void uncertainDetectionWithoutLockCannotBeRetried() {
        // 준비
        var segment = retryableSegment();
        when(segment.getDetectedLanguage()).thenReturn("ja");
        when(segment.getLanguageConfidence()).thenReturn(0.49);

        // 실행 및 검증
        assertThatThrownBy(() -> retry.prepare(1L, "voice", 2L))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(VoiceErrorCode.RETRY_LANGUAGE_UNAVAILABLE));
    }

    @Test
    void knownUnsupportedLanguageCannotFallBackToAnOldSupportedLock() {
        // 준비: 기존 데이터에 비지원 언어가 남아 있어도 과거 영어 lock으로 번역하지 않는다.
        var segment = retryableSegment();
        when(segment.getDetectedLanguage()).thenReturn("fr");
        when(segment.getLanguageConfidence()).thenReturn(0.9);
        when(segment.getLockedLanguage()).thenReturn("en");

        // 실행 및 검증
        assertThatThrownBy(() -> retry.prepare(1L, "voice", 2L))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(VoiceErrorCode.RETRY_LANGUAGE_UNAVAILABLE));
    }

    private VoiceSegment retryableSegment() {
        var segment = mock(VoiceSegment.class);
        when(segment.getStatus()).thenReturn(VoiceSegmentStatus.TRANSLATION_FAILED);
        when(segment.getSourceText()).thenReturn("A transcript");
        when(segment.getTargetLanguage()).thenReturn("ko");
        when(segment.getErrorCode()).thenReturn("VOICE_AI_TRANSLATION_TIMEOUT");
        when(segments.findByIdAndSession_IdAndSession_User_Id(2L, "voice", 1L)).thenReturn(Optional.of(segment));
        return segment;
    }
}
