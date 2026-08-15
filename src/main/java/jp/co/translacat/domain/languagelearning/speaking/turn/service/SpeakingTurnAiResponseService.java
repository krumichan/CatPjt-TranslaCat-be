package jp.co.translacat.domain.languagelearning.speaking.turn.service;

import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingConversationResultDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.request.AiSpeakingTtsRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.response.AiSpeakingTurnProcessResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.port.SpeakingAiClient;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingStage;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.session.model.SpeakingSessionPolicySnapshot;
import jp.co.translacat.domain.languagelearning.speaking.turn.entity.SpeakingTurn;
import jp.co.translacat.domain.languagelearning.speaking.usage.service.SpeakingAiUsageCommandService;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SpeakingTurnAiResponseService {

    private final SpeakingAiClient speakingAiClient;
    private final SpeakingTurnAudioService audioService;
    private final SpeakingAiUsageCommandService usageCommandService;
    private final LanguageLearningJsonCodec jsonCodec;

    public void apply(
            SpeakingSession session,
            SpeakingTurn turn,
            AiSpeakingTurnProcessResponseDto response,
            SpeakingSessionPolicySnapshot snapshot
    ) {
        if (response == null) {
            turn.markFailed(
                    SpeakingStage.CONVERSATION,
                    LanguageLearningErrorCode.STT_FAILED,
                    "AI Turn 응답이 없습니다."
            );
            return;
        }

        usageCommandService.record(
                session,
                turn.getId(),
                response.usage(),
                turn.getManualRetryCount()
        );
        applyTranscript(turn, response);
        applyAssistant(session, turn, response, snapshot);

        if ("READY".equalsIgnoreCase(response.status())) {
            turn.markReady(jsonCodec.write(response.usage()));
            session.registerCompletedTurn(
                    turn.getDurationSeconds(),
                    response.conversation() == null
                            ? session.getSessionSummary()
                            : response.conversation().sessionSummary(),
                    jsonCodec.write(response.usage())
            );
            return;
        }

        SpeakingStage failedStage = parseStage(response.failedStage());
        turn.markPartialFailure(
                failedStage,
                response.error() == null
                        ? stageErrorCode(failedStage)
                        : response.error().code(),
                truncate(
                        response.error() == null
                                ? "Speaking Turn 처리 중 일부 단계가 실패했습니다."
                                : response.error().message(),
                        1000
                ),
                jsonCodec.write(response.usage())
        );
    }

    public void retryTts(
            SpeakingSession session,
            SpeakingTurn turn,
            SpeakingSessionPolicySnapshot snapshot
    ) {
        var response = speakingAiClient.synthesize(
                new AiSpeakingTtsRequestDto(
                        "speaking-tts-" + turn.getId()
                                + "-retry-" + turn.getManualRetryCount(),
                        turn.getIdempotencyKey() + ":tts:"
                                + turn.getManualRetryCount(),
                        String.valueOf(session.getId()),
                        turn.getAssistantText(),
                        session.getLearningLanguage(),
                        session.getVoiceId(),
                        session.getPlaybackSpeed(),
                        snapshot.automaticRetryLimitPerStage(),
                        turn.getManualRetryCount()
                )
        );

        usageCommandService.record(
                session,
                turn.getId(),
                response == null ? null : response.usage(),
                turn.getManualRetryCount()
        );
        if (response == null
                || response.audio() == null
                || response.audio().audioReference() == null) {
            turn.markPartialFailure(
                    SpeakingStage.TTS,
                    LanguageLearningErrorCode.TTS_FAILED,
                    "TTS 재생성에 실패했습니다.",
                    response == null ? "{}" : jsonCodec.write(response.usage())
            );
            return;
        }

        byte[] bytes = speakingAiClient.getAudio(
                response.audio().audioReference()
        );
        String audioKey = audioService.storeAssistantAudio(
                session,
                turn,
                bytes,
                response.audio().contentType()
        );
        turn.applyAssistant(
                turn.getAssistantText(),
                audioKey,
                response.audio().contentType(),
                turn.getConversationJson()
        );
        turn.setAssistantAudioRetention(
                LocalDateTime.now()
                        .plusDays(snapshot.rawAudioRetentionDays())
        );
        turn.markReady(jsonCodec.write(response.usage()));
        session.registerCompletedTurn(
                turn.getDurationSeconds(),
                conversationSummary(turn),
                jsonCodec.write(response.usage())
        );
    }

    private void applyTranscript(
            SpeakingTurn turn,
            AiSpeakingTurnProcessResponseDto response
    ) {
        if (response.transcript() == null) {
            return;
        }
        turn.applyTranscript(
                response.transcript().text(),
                response.transcript().confidence(),
                jsonCodec.write(response.transcript().segments()),
                jsonCodec.write(response.transcript().metadata())
        );
    }

    private void applyAssistant(
            SpeakingSession session,
            SpeakingTurn turn,
            AiSpeakingTurnProcessResponseDto response,
            SpeakingSessionPolicySnapshot snapshot
    ) {
        if (response.assistant() == null && response.conversation() == null) {
            return;
        }

        String audioObjectKey = null;
        if (response.assistant() != null
                && response.assistant().audio() != null
                && response.assistant().audio().audioReference() != null) {
            byte[] bytes = speakingAiClient.getAudio(
                    response.assistant().audio().audioReference()
            );
            audioObjectKey = audioService.storeAssistantAudio(
                    session,
                    turn,
                    bytes,
                    response.assistant().audio().contentType()
            );
        }

        turn.applyAssistant(
                response.assistant() == null
                        ? null
                        : response.assistant().text(),
                audioObjectKey,
                response.assistant() == null
                        || response.assistant().audio() == null
                        ? null
                        : response.assistant().audio().contentType(),
                jsonCodec.write(response.conversation())
        );
        if (audioObjectKey != null) {
            turn.setAssistantAudioRetention(
                    LocalDateTime.now()
                            .plusDays(snapshot.rawAudioRetentionDays())
            );
        }
    }

    private String conversationSummary(SpeakingTurn turn) {
        if (turn.getConversationJson() == null
                || turn.getConversationJson().isBlank()
                || turn.getConversationJson().equals("{}")) {
            return turn.getSession().getSessionSummary();
        }
        AiSpeakingConversationResultDto conversation = jsonCodec.read(
                turn.getConversationJson(),
                AiSpeakingConversationResultDto.class
        );
        return conversation == null || conversation.sessionSummary() == null
                ? turn.getSession().getSessionSummary()
                : conversation.sessionSummary();
    }

    private SpeakingStage parseStage(String value) {
        if (value == null || value.isBlank()) {
            return SpeakingStage.CONVERSATION;
        }
        try {
            return SpeakingStage.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SpeakingStage.CONVERSATION;
        }
    }

    private String stageErrorCode(SpeakingStage stage) {
        return switch (stage) {
            case STT, AUDIO_VALIDATION -> LanguageLearningErrorCode.STT_FAILED;
            case TTS -> LanguageLearningErrorCode.TTS_FAILED;
            default -> LanguageLearningErrorCode.SPEAKING_EVALUATION_FAILED;
        };
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
