package jp.co.translacat.domain.languagelearning.speaking.session.service;

import jp.co.translacat.domain.languagelearning.activity.entity.LearningActivity;
import jp.co.translacat.domain.languagelearning.activity.service.LearningActivityCommandService;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingEvaluationEligibilityDto;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.ConversationStartMode;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.CorrectionMode;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingEvaluationStatus;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingSessionStatus;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.event.SpeakingEvaluationRequestedEvent;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.policy.SpeakingEvaluationEligibilityPolicy;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.session.model.SpeakingSessionPolicySnapshot;
import jp.co.translacat.domain.languagelearning.speaking.turn.service.SpeakingTurnQueryService;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.global.exception.BusinessException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpeakingSessionCompletionCommandServiceTest {

    @Mock
    private SpeakingSessionQueryService sessionQueryService;
    @Mock
    private SpeakingSessionLifecycleService lifecycleService;
    @Mock
    private SpeakingSessionPolicySnapshotService snapshotService;
    @Mock
    private LearningActivityCommandService activityCommandService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private LanguageLearningJsonCodec jsonCodec;
    @Mock
    private SpeakingTurnQueryService turnQueryService;
    @Mock
    private SpeakingEvaluationEligibilityPolicy eligibilityPolicy;
    @Mock
    private LearningActivity activity;

    private SpeakingSessionCompletionCommandService service;
    private SpeakingSession session;

    @BeforeEach
    void setUp() {
        service = new SpeakingSessionCompletionCommandService(
                sessionQueryService,
                lifecycleService,
                snapshotService,
                activityCommandService,
                eventPublisher,
                jsonCodec,
                turnQueryService,
                eligibilityPolicy
        );
        session = session();

        when(sessionQueryService.getOwnedEntity(7L, 301L))
                .thenReturn(session);
        when(snapshotService.read(session)).thenReturn(snapshot());
        when(turnQueryService.getEntities(301L)).thenReturn(List.of());
    }

    @Test
    void insufficientSessionCanCompleteWithoutEvaluation() {
        when(eligibilityPolicy.evaluate(List.of())).thenReturn(eligibility(false));
        when(activityCommandService.getOrCreate(
                anyLong(),
                any(),
                any(),
                any(),
                any(),
                anyLong(),
                any(),
                any()
        )).thenReturn(activity);
        when(jsonCodec.write(any())).thenReturn("{\"evaluationSkipped\":true}");

        SpeakingSession completed = service.complete(7L, 301L, true);

        assertThat(completed.getStatus()).isEqualTo(SpeakingSessionStatus.COMPLETED);
        assertThat(completed.getEvaluationStatus())
                .isEqualTo(SpeakingEvaluationStatus.NOT_REQUESTED);
        verify(activity).updateMetadataJson("{\"evaluationSkipped\":true}");
        verify(eventPublisher, never())
                .publishEvent(any(SpeakingEvaluationRequestedEvent.class));
    }

    @Test
    void eligibleSessionCannotSkipEvaluation() {
        when(eligibilityPolicy.evaluate(List.of())).thenReturn(eligibility(true));

        assertThatThrownBy(() -> service.complete(7L, 301L, true))
                .isInstanceOf(BusinessException.class);

        assertThat(session.getStatus()).isEqualTo(SpeakingSessionStatus.IN_PROGRESS);
        verify(activityCommandService, never()).getOrCreate(
                anyLong(),
                any(),
                any(),
                any(),
                any(),
                anyLong(),
                any(),
                any()
        );
    }

    private AiSpeakingEvaluationEligibilityDto eligibility(boolean eligible) {
        return new AiSpeakingEvaluationEligibilityDto(
                eligible ? 5 : 2,
                eligible ? 60 : 16,
                1.0,
                5,
                60,
                0.8,
                0.7,
                eligible,
                eligible
                        ? List.of()
                        : List.of("VALID_USER_TURNS", "VALID_USER_SPEECH_SECONDS")
        );
    }

    private SpeakingSessionPolicySnapshot snapshot() {
        return new SpeakingSessionPolicySnapshot(
                true,
                30,
                5,
                10,
                20,
                1,
                60,
                10L * 1024L * 1024L,
                7,
                30,
                2,
                2,
                1,
                30,
                30,
                60
        );
    }

    private SpeakingSession session() {
        User user = User.createLocalUser(
                "completion@test.local",
                "pw",
                "completion",
                Role.USER,
                "COMPLETION001"
        );
        return SpeakingSession.create(
                user,
                null,
                "session-key",
                LocalDate.of(2026, 8, 15),
                "Free Talk",
                "FREE_TALK",
                1,
                "Free Talk",
                null,
                null,
                "[]",
                "ko",
                "ja",
                ConversationStartMode.USER_FIRST,
                ConversationStartMode.USER_FIRST,
                CorrectionMode.CONVERSATION,
                5,
                20,
                "Kore",
                "NORMAL",
                "{}",
                "{}"
        );
    }
}
