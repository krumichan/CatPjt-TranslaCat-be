package jp.co.translacat.domain.languagelearning.level.facade;

import jp.co.translacat.domain.languagelearning.level.dto.request.LevelAnswerRequestDto;
import jp.co.translacat.domain.languagelearning.level.dto.request.LevelTestStartRequestDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.*;
import jp.co.translacat.domain.languagelearning.level.port.LevelTestGateway;
import jp.co.translacat.domain.languagelearning.listening.audio.model.ListeningAudioObject;
import jp.co.translacat.domain.languagelearning.profile.service.LevelTestBaselineBridge;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 외부 URL/DTO는 유지하고 업무 처리와 저장은 LL로 전달한다. 긴 AI 호출 중 Core 트랜잭션을 열지 않는다.
 */
@Service
@RequiredArgsConstructor
public class LanguageLearningLevelTestFacade {
    private final LevelTestGateway gateway;
    private final LevelTestBaselineBridge baselineBridge;

    public LevelStatusResponseDto getStatus(Long userId) {
        var state = baselineBridge.synchronize(userId);
        var value = gateway.status(userId);
        return new LevelStatusResponseDto(state == null ? value.profileState() : state,
                value.initialLevelTestCompleted(), value.recheckRecommended(), value.activeSessionId(),
                value.currentQuestionNumber(), value.baseLevelScore(), value.proficiencyBand());
    }

    public LevelSessionResponseDto start(Long userId, LevelTestStartRequestDto request) {
        return gateway.start(userId, request);
    }

    public LevelSessionResponseDto getSession(Long userId, Long sessionId) {
        return gateway.session(userId, sessionId);
    }

    public LevelQuestionResponseDto getCurrent(Long userId, Long sessionId) {
        return gateway.current(userId, sessionId);
    }

    public LevelAnswerResultResponseDto submit(Long userId, Long sessionId, Long itemId,
                                               LevelAnswerRequestDto request) {
        var value = gateway.submit(userId, sessionId, itemId, request);
        if (value.completed()) baselineBridge.requireCompleted(userId);
        return value;
    }

    public LevelAudioAnswerResultResponseDto submitAudio(Long userId, Long sessionId, Long itemId, MultipartFile audio,
                                                         Integer durationMs, String key) {
        if (audio == null
                || audio.isEmpty()
                || audio.getSize() > 10 * 1024 * 1024
                || durationMs == null
                || durationMs <= 0
                || key == null
                || key.isBlank()) {
            throw new BusinessException("녹음 크기와 길이, 재전송 키를 확인해 주세요.",
                    LanguageLearningErrorCode.LEVEL_TEST_AUDIO_INVALID);
        }
        try {
            var value = gateway.submitAudio(userId, sessionId, itemId, audio.getBytes(),
                    audio.getContentType() == null ? "application/octet-stream" : audio.getContentType(), durationMs,
                    key);
            if (value.completed()) baselineBridge.requireCompleted(userId);
            return value;
        } catch (IOException error) {
            throw new BusinessException("녹음을 읽을 수 없습니다.", LanguageLearningErrorCode.LEVEL_TEST_AUDIO_INVALID);
        }
    }

    public LevelAnswerResultResponseDto retryEvaluation(Long userId, Long sessionId, Long itemId) {
        var value = gateway.retry(userId, sessionId, itemId);
        if (value.completed()) baselineBridge.requireCompleted(userId);
        return value;
    }

    public LevelTestResultResponseDto getResult(Long userId, Long sessionId) {
        var value = gateway.result(userId, sessionId);
        baselineBridge.requireCompleted(userId);
        return value;
    }

    public List<LevelTestHistoryItemResponseDto> getHistory(Long userId) {
        return gateway.history(userId);
    }

    public LevelTestHistoryDetailResponseDto getHistoryDetail(Long userId, Long sessionId) {
        return gateway.detail(userId, sessionId);
    }

    public ListeningAudioObject getReferenceAudio(Long userId, Long itemId) {
        return audio(userId, itemId, "reference-audio");
    }

    public ListeningAudioObject getAnswerAudio(Long userId, Long itemId) {
        return audio(userId, itemId, "answer-audio");
    }

    public ListeningAudioObject getModelAnswerAudio(Long userId, Long itemId) {
        return audio(userId, itemId, "model-answer-audio");
    }

    private ListeningAudioObject audio(Long userId, Long itemId, String kind) {
        var value = gateway.audio(userId, itemId, kind);
        return new ListeningAudioObject("level-test:" + itemId, value.bytes(), value.contentType());
    }
}
