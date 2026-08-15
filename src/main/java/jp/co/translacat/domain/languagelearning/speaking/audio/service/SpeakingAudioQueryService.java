package jp.co.translacat.domain.languagelearning.speaking.audio.service;

import jp.co.translacat.domain.languagelearning.speaking.audio.model.SpeakingAudioObject;
import jp.co.translacat.domain.languagelearning.speaking.audio.port.SpeakingAudioStoragePort;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.session.service.SpeakingSessionQueryService;
import jp.co.translacat.domain.languagelearning.speaking.turn.entity.SpeakingTurn;
import jp.co.translacat.domain.languagelearning.speaking.turn.service.SpeakingTurnQueryService;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SpeakingAudioQueryService {

    private final SpeakingAudioStoragePort storagePort;
    private final SpeakingSessionQueryService sessionQueryService;
    private final SpeakingTurnQueryService turnQueryService;

    public SpeakingAudioObject getOpeningAudio(
            Long userId,
            Long sessionId
    ) {
        SpeakingSession session = sessionQueryService.getOwnedEntity(
                userId,
                sessionId
        );
        if (session.getOpeningAssistantAudioObjectKey() == null) {
            throw notFound();
        }
        return storagePort.load(
                session.getOpeningAssistantAudioObjectKey(),
                "audio/wav"
        );
    }

    public SpeakingAudioObject getAssistantAudio(
            Long userId,
            Long sessionId,
            Long turnId
    ) {
        SpeakingTurn turn = turnQueryService.getOwnedEntity(
                userId,
                sessionId,
                turnId
        );
        if (turn.getAssistantAudioObjectKey() == null) {
            throw notFound();
        }
        return storagePort.load(
                turn.getAssistantAudioObjectKey(),
                turn.getAssistantAudioContentType() == null
                        ? "audio/wav"
                        : turn.getAssistantAudioContentType()
        );
    }

    private BusinessException notFound() {
        return new BusinessException(
                "Speaking Audio를 찾을 수 없습니다.",
                LanguageLearningErrorCode.INVALID_AUDIO
        );
    }
}
