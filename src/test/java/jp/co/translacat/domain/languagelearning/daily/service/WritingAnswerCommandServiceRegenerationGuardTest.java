package jp.co.translacat.domain.languagelearning.daily.service;

import jakarta.persistence.EntityManager;

import jp.co.translacat.domain.languagelearning.daily.dto.request.AnswerSubmitRequestDto;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingItem;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingSet;
import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingItemRepository;
import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingSetRepository;
import jp.co.translacat.domain.languagelearning.daily.repository.WritingAnswerRepository;
import jp.co.translacat.domain.languagelearning.daily.repository.WritingEvaluationRepository;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningAdminSetting;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningAdminSettingQueryService;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningUserSettingQueryService;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.exception.BusinessException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WritingAnswerCommandServiceRegenerationGuardTest {

    @Mock private DailyWritingItemRepository itemRepository;
    @Mock private DailyWritingSetRepository dailySetRepository;
    @Mock private WritingAnswerRepository answerRepository;
    @Mock private WritingEvaluationRepository evaluationRepository;
    @Mock private LanguageLearningAdminSettingQueryService adminSettingQueryService;
    @Mock private LanguageLearningUserSettingQueryService userSettingQueryService;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private DailyWritingItemRevisionService itemRevisionService;
    @Mock private EntityManager entityManager;
    @Mock private DailyWritingItem item;
    @Mock private DailyWritingSet dailySet;
    @Mock private LanguageLearningAdminSetting adminSetting;

    private WritingAnswerCommandService service;

    @BeforeEach
    void setUp() {
        service = new WritingAnswerCommandService(
                itemRepository,
                dailySetRepository,
                answerRepository,
                evaluationRepository,
                adminSettingQueryService,
                userSettingQueryService,
                userRepository,
                eventPublisher,
                itemRevisionService,
                entityManager
        );
        when(adminSettingQueryService.getOrCreateEntity())
                .thenReturn(adminSetting);
        when(adminSetting.isAiEvaluationEnabled()).thenReturn(true);
        when(itemRepository.findByIdAndDailySetUserId(30L, 10L))
                .thenReturn(Optional.of(item));
        when(item.getDailySet()).thenReturn(dailySet);
        when(dailySet.getId()).thenReturn(20L);
        when(dailySetRepository.findLockedById(20L))
                .thenReturn(Optional.of(dailySet));
    }

    @Test
    void answerIsRejectedWhileRegenerationOwnsSet() {
        when(itemRevisionService.revision(item)).thenReturn("rev-1");
        when(dailySet.isRegenerationActive(any())).thenReturn(true);

        assertThatThrownBy(() -> service.submit(
                10L,
                30L,
                new AnswerSubmitRequestDto("답변", "rev-1")
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(throwable -> assertThat(
                        ((BusinessException) throwable).getErrorCode()
                ).isEqualTo(
                        "LANGUAGE_LEARNING_WRITING_REGENERATION_IN_PROGRESS"
                ));

        verify(entityManager, never()).refresh(any());
        verify(answerRepository, never()).save(any());
    }

    @Test
    void itemMutationWhileWaitingForSetLockIsRejected() {
        when(dailySet.isRegenerationActive(any())).thenReturn(false);
        when(itemRevisionService.revision(item))
                .thenReturn("before", "after");

        assertThatThrownBy(() -> service.submit(
                10L,
                30L,
                new AnswerSubmitRequestDto("답변", null)
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(throwable -> assertThat(
                        ((BusinessException) throwable).getErrorCode()
                ).isEqualTo(
                        "LANGUAGE_LEARNING_WRITING_ITEM_STALE"
                ));

        verify(entityManager).refresh(item);
        verify(answerRepository, never()).save(any());
    }

    @Test
    void clientRevisionDetectsAlreadyRegeneratedProblem() {
        when(dailySet.isRegenerationActive(any())).thenReturn(false);
        when(itemRevisionService.revision(item))
                .thenReturn("current", "current");

        assertThatThrownBy(() -> service.submit(
                10L,
                30L,
                new AnswerSubmitRequestDto("답변", "old-client")
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(throwable -> assertThat(
                        ((BusinessException) throwable).getErrorCode()
                ).isEqualTo(
                        "LANGUAGE_LEARNING_WRITING_ITEM_STALE"
                ));
    }
}
