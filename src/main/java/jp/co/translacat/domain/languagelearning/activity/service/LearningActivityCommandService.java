package jp.co.translacat.domain.languagelearning.activity.service;

import jp.co.translacat.domain.languagelearning.activity.model.GrowthActivityDraft;
import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.growth.model.*;
import jp.co.translacat.domain.languagelearning.growth.port.GrowthCommands;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LearningActivityCommandService {
    private final GrowthCommands commands;
    private final LanguageLearningJsonCodec codec;
    @Transactional public GrowthActivityDraft getOrCreate(Long userId, LearningSource source, String referenceId,
            LocalDate date, String title, long duration, LocalDateTime started, LocalDateTime completed) {
        var draft = new GrowthActivityDraft(userId, source, referenceId, date, title, duration, started, completed);
        String key = "ACTIVITY:" + UUID.randomUUID();
        commands.stage(userId, key, () -> new GrowthOperation(key, "ACTIVITY_RECORDED", GrowthFacts.fields("activity", draft.payload(), "metrics", null)));
        return draft;
    }
    /** 상태 변경도 원본 식별 정보를 포함하여 이전 Core 완료 Session의 결과를 안전하게 받아들인다. */
    @Transactional public void speakingStatus(SpeakingSession session, boolean failed) {
        var draft = getOrCreate(session.getUser().getId(), LearningSource.SPEAKING, session.getId().toString(), session.getLearningDate(),
            session.getTopicTitle(), session.getTotalDurationSeconds(), session.getStartedAt(), session.getCompletedAt());
        draft.updateMetadataJson(speakingMetadata(session));
        if (failed) draft.markEvaluationFailed(); else draft.markEvaluating();
    }
    public GrowthActivityDraft speakingResult(SpeakingSession session) {
        var draft = new GrowthActivityDraft(session.getUser().getId(), LearningSource.SPEAKING, session.getId().toString(), session.getLearningDate(),
            session.getTopicTitle(), session.getTotalDurationSeconds(), session.getStartedAt(), session.getCompletedAt());
        draft.updateMetadataJson(speakingMetadata(session));
        return draft;
    }
    private String speakingMetadata(SpeakingSession session) {
        return codec.write(GrowthFacts.fields("topicCategory", session.getTopicCategory(), "conversationStartMode", session.getConversationStartMode().name(),
            "resolvedStartMode", session.getResolvedStartMode().name(), "correctionMode", session.getCorrectionMode().name(), "selectedKeywords", session.getSelectedKeywordsJson(),
            "evaluationSkipped", false, "resultKind", session.getResultKind().name(), "resultPolicyVersion", session.getResultPolicyVersion()));
    }
}
