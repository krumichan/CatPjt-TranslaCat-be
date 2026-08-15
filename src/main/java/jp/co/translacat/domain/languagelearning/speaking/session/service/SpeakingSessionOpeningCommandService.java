package jp.co.translacat.domain.languagelearning.speaking.session.service;

import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.response.AiSpeakingSessionStartResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.port.SpeakingAiClient;
import jp.co.translacat.domain.languagelearning.speaking.audio.port.SpeakingAudioStoragePort;
import jp.co.translacat.domain.languagelearning.speaking.audio.service.SpeakingAudioKeyFactory;
import jp.co.translacat.domain.languagelearning.speaking.session.dto.request.SpeakingSessionCreateRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.session.factory.SpeakingSessionAiRequestFactory;
import jp.co.translacat.domain.languagelearning.speaking.session.model.SpeakingSessionCreationContext;
import jp.co.translacat.domain.languagelearning.speaking.usage.service.SpeakingAiUsageCommandService;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SpeakingSessionOpeningCommandService {

    private final SpeakingSessionAiRequestFactory aiRequestFactory;
    private final LanguageLearningJsonCodec jsonCodec;
    private final SpeakingAiClient speakingAiClient;
    private final SpeakingAudioStoragePort audioStoragePort;
    private final SpeakingAudioKeyFactory audioKeyFactory;
    private final SpeakingAiUsageCommandService usageCommandService;

    public void start(
            SpeakingSession session,
            SpeakingSessionCreateRequestDto request,
            SpeakingSessionCreationContext context
    ) {
        var response = speakingAiClient.startSession(
                aiRequestFactory.create(
                        session,
                        request,
                        context.topic(),
                        context.learningProfile(),
                        context.selectedKeywords(),
                        context.policySnapshot()
                )
        );
        if (response == null) {
            throw new BusinessException(
                    "Speaking Session 시작 응답이 없습니다.",
                    LanguageLearningErrorCode.STT_FAILED
            );
        }

        usageCommandService.record(session, null, response.usage(), 0);
        String audioObjectKey = storeOpeningAudio(session, response);
        session.storeOpeningAssistant(
                response.assistant() == null
                        ? null
                        : response.assistant().text(),
                audioObjectKey,
                audioObjectKey == null
                        ? null
                        : LocalDateTime.now().plusDays(
                                context.policySnapshot().rawAudioRetentionDays()
                        ),
                response.conversation() == null
                        ? null
                        : response.conversation().sessionSummary(),
                jsonCodec.write(response.usage())
        );
    }

    private String storeOpeningAudio(
            SpeakingSession session,
            AiSpeakingSessionStartResponseDto response
    ) {
        if (response.assistant() == null
                || response.assistant().audio() == null
                || response.assistant().audio().audioReference() == null) {
            return null;
        }

        byte[] audio = speakingAiClient.getAudio(
                response.assistant().audio().audioReference()
        );
        if (audio == null || audio.length == 0) {
            return null;
        }

        String key = audioKeyFactory.assistantTurn(
                session.getUser().getId(),
                session.getId(),
                "opening"
        );
        audioStoragePort.store(
                key,
                audio,
                response.assistant().audio().contentType() == null
                        ? "audio/wav"
                        : response.assistant().audio().contentType()
        );
        return key;
    }
}
