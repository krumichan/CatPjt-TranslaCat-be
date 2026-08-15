package jp.co.translacat.domain.languagelearning.speaking.session.service;

import jp.co.translacat.domain.languagelearning.activity.entity.LearningActivity;
import jp.co.translacat.domain.languagelearning.activity.service.LearningActivityCommandService;
import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.event.SpeakingEvaluationRequestedEvent;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.policy.SpeakingEvaluationEligibilityPolicy;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.session.model.SpeakingSessionPolicySnapshot;
import jp.co.translacat.domain.languagelearning.speaking.turn.service.SpeakingTurnQueryService;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SpeakingSessionCompletionCommandService {

    private final SpeakingSessionQueryService sessionQueryService;
    private final SpeakingSessionLifecycleService lifecycleService;
    private final SpeakingSessionPolicySnapshotService snapshotService;
    private final LearningActivityCommandService activityCommandService;
    private final ApplicationEventPublisher eventPublisher;
    private final LanguageLearningJsonCodec jsonCodec;
    private final SpeakingTurnQueryService turnQueryService;
    private final SpeakingEvaluationEligibilityPolicy eligibilityPolicy;

    @Transactional
    public SpeakingSession complete(Long userId, Long sessionId) {
        return complete(userId, sessionId, false);
    }

    @Transactional
    public SpeakingSession complete(
            Long userId,
            Long sessionId,
            boolean skipEvaluation
    ) {
        SpeakingSession session = sessionQueryService.getOwnedEntity(
                userId,
                sessionId
        );
        lifecycleService.requireActive(session);
        SpeakingSessionPolicySnapshot snapshot = snapshotService.read(session);
        var eligibility = eligibilityPolicy.evaluate(
                turnQueryService.getEntities(sessionId)
        );
        if (skipEvaluation
                && snapshot.speakingEvaluationEnabled()
                && eligibility.eligibleBeforeAi()) {
            throw new BusinessException(
                    "평가 가능 조건을 충족한 Session은 평가 없이 종료할 수 없습니다.",
                    LanguageLearningErrorCode.SPEAKING_EVALUATION_SKIP_NOT_ALLOWED
            );
        }
        boolean evaluate = snapshot.speakingEvaluationEnabled()
                && !skipEvaluation;
        session.complete(evaluate);

        LearningActivity activity = activityCommandService.getOrCreate(
                userId,
                LearningSource.SPEAKING,
                String.valueOf(session.getId()),
                session.getLearningDate(),
                session.getTopicTitle(),
                session.getTotalDurationSeconds(),
                session.getStartedAt(),
                session.getCompletedAt()
        );
        activity.updateMetadataJson(metadata(session, skipEvaluation));
        if (evaluate) {
            activity.markEvaluating();
            eventPublisher.publishEvent(
                    new SpeakingEvaluationRequestedEvent(
                            session.getId(),
                            0
                    )
            );
        }
        return session;
    }

    private String metadata(
            SpeakingSession session,
            boolean evaluationSkipped
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("topicCategory", session.getTopicCategory());
        metadata.put(
                "conversationStartMode",
                session.getConversationStartMode().name()
        );
        metadata.put(
                "resolvedStartMode",
                session.getResolvedStartMode().name()
        );
        metadata.put("correctionMode", session.getCorrectionMode().name());
        metadata.put("selectedKeywords", session.getSelectedKeywordsJson());
        metadata.put("evaluationSkipped", evaluationSkipped);
        return jsonCodec.write(metadata);
    }
}
