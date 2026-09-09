package jp.co.translacat.domain.languagelearning.listening.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.listening.attempt.entity.ListeningItemAttempt;
import jp.co.translacat.domain.languagelearning.listening.attempt.repository.ListeningItemAttemptRepository;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningDailySetStatus;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningItemStatus;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.ListeningDailySet;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.ListeningItem;
import jp.co.translacat.domain.languagelearning.listening.daily.service.ListeningDailySetQueryService;
import jp.co.translacat.domain.languagelearning.listening.evaluation.repository.ListeningTaskEvaluationRepository;
import jp.co.translacat.domain.languagelearning.listening.response.repository.ListeningTaskResponseRepository;
import jp.co.translacat.domain.languagelearning.listening.session.entity.ListeningSession;
import jp.co.translacat.domain.languagelearning.listening.setting.entity.ListeningPolicySetting;
import jp.co.translacat.domain.languagelearning.listening.setting.service.ListeningPolicySettingQueryService;
import jp.co.translacat.domain.user.entity.User;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ListeningViewMapperProgressiveTest {

    @Test
    void sessionExposesLogicalOfficialSlotsAndIndependentGenerationAndTtsStates() {
        ListeningItemAttemptRepository attempts = mock(ListeningItemAttemptRepository.class);
        ListeningDailySetQueryService sets = mock(ListeningDailySetQueryService.class);
        ListeningPolicySettingQueryService settings = mock(ListeningPolicySettingQueryService.class);
        ListeningPolicySetting policy = mock(ListeningPolicySetting.class);
        when(policy.getResumeHours()).thenReturn(24);
        when(settings.get()).thenReturn(policy);
        ListeningDailySet dailySet = mock(ListeningDailySet.class);
        when(dailySet.getId()).thenReturn(20L);
        when(dailySet.getTargetItemCount()).thenReturn(5);
        when(dailySet.getStatus()).thenReturn(ListeningDailySetStatus.PARTIAL);
        when(dailySet.getFailureReason()).thenReturn("slot 4 failed");
        ListeningSession session = ListeningSession.create(mock(User.class), dailySet,
                "[\"SUMMARY\"]", "{}", "[]", "key", LocalDateTime.now());
        ReflectionTestUtils.setField(session, "id", 30L);
        when(attempts.findAllBySessionIdOrderByItemItemIndexAscAttemptNoAsc(30L))
                .thenReturn(List.of(attempt(1, true), attempt(1, true), attempt(2, false), attempt(3, true)));
        ListeningItem pending = mock(ListeningItem.class);
        when(pending.getStatus()).thenReturn(ListeningItemStatus.TTS_PENDING);
        ListeningItem ready = mock(ListeningItem.class);
        when(ready.getStatus()).thenReturn(ListeningItemStatus.READY);
        when(sets.activeItems(20L)).thenReturn(List.of(ready, pending));
        when(sets.generationInProgress(20L)).thenReturn(true);
        ListeningViewMapper mapper = new ListeningViewMapper(attempts,
                mock(ListeningTaskResponseRepository.class), mock(ListeningTaskEvaluationRepository.class),
                settings, new LanguageLearningJsonCodec(new ObjectMapper()), sets);

        var view = mapper.session(session);

        assertThat(view.targetItemCount()).isEqualTo(5);
        assertThat(view.attachedItemCount()).isEqualTo(2);
        assertThat(view.pendingItemCount()).isEqualTo(1);
        assertThat(view.dailySetStatus()).isEqualTo(ListeningDailySetStatus.PARTIAL);
        assertThat(view.generationFailureMessage()).isEqualTo("slot 4 failed");
        assertThat(view.generationInProgress()).isTrue();
        assertThat(view.attempts()).extracting(value -> value.itemIndex()).containsExactly(1, 1, 2, 3);
    }

    private ListeningItemAttempt attempt(int index, boolean official) {
        ListeningItemAttempt attempt = mock(ListeningItemAttempt.class);
        ListeningItem item = mock(ListeningItem.class);
        when(item.getItemIndex()).thenReturn(index);
        when(attempt.getItem()).thenReturn(item);
        when(attempt.isOfficial()).thenReturn(official);
        return attempt;
    }
}
