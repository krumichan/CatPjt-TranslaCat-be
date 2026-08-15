package jp.co.translacat.domain.languagelearning.speaking.usage.service;

import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingStageUsageDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingUsageDto;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingStage;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.usage.entity.SpeakingAiUsageLog;
import jp.co.translacat.domain.languagelearning.speaking.usage.repository.SpeakingAiUsageLogRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SpeakingAiUsageCommandService {

    private final SpeakingAiUsageLogRepository usageRepository;

    @Transactional
    public void record(
            SpeakingSession session,
            Long turnId,
            AiSpeakingUsageDto usage,
            int manualRetryAttempt
    ) {
        if (usage == null) {
            return;
        }
        save(session, turnId, SpeakingStage.STT, usage.stt(), manualRetryAttempt);
        save(
                session,
                turnId,
                SpeakingStage.CONVERSATION,
                usage.conversation(),
                manualRetryAttempt
        );
        save(session, turnId, SpeakingStage.TTS, usage.tts(), manualRetryAttempt);
        save(
                session,
                turnId,
                SpeakingStage.EVALUATION,
                usage.evaluation(),
                manualRetryAttempt
        );
    }

    private void save(
            SpeakingSession session,
            Long turnId,
            SpeakingStage stage,
            AiSpeakingStageUsageDto value,
            int manualRetryAttempt
    ) {
        if (value == null) {
            return;
        }
        usageRepository.save(SpeakingAiUsageLog.create(
                session,
                turnId,
                stage,
                value.latencyMs(),
                value.inputTokens(),
                value.outputTokens(),
                value.audioSeconds(),
                value.ttsCharacters(),
                value.ttsAudioSeconds(),
                value.provider(),
                value.model(),
                value.promptVersion(),
                value.evaluationVersion(),
                manualRetryAttempt
        ));
    }
}
