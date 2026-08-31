package jp.co.translacat.domain.languagelearning.level.pool.service;

import jp.co.translacat.domain.languagelearning.level.policy.LevelTestRecipe;
import jp.co.translacat.domain.languagelearning.level.pool.audio.service.LevelTestReferenceAudioUploadService;
import jp.co.translacat.domain.languagelearning.level.pool.policy.LevelTestQuestionPoolPolicy;
import jp.co.translacat.domain.languagelearning.level.pool.entity.LevelTestQuestionPool;
import jp.co.translacat.domain.languagelearning.level.pool.policy.LevelTestQuestionPoolTargetPlanner;
import jp.co.translacat.domain.languagelearning.level.service.LevelTestQuestionService;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningUserSetting;
import jp.co.translacat.domain.languagelearning.setting.repository.LanguageLearningUserSettingRepository;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LevelTestQuestionPoolReplenishmentServiceTest {

    @Test
    void plansAtMostConfiguredBatchSizeFromMostDeficientBuckets() {
        LanguageLearningUserSettingRepository userSettingRepository =
                mock(LanguageLearningUserSettingRepository.class);
        LevelTestQuestionPoolQueryService poolQueryService =
                mock(LevelTestQuestionPoolQueryService.class);
        LevelTestQuestionPoolPolicy poolPolicy =
                mock(LevelTestQuestionPoolPolicy.class);
        LevelTestQuestionPoolCommandService poolCommandService =
                mock(LevelTestQuestionPoolCommandService.class);
        LevelTestQuestionPoolHealthService healthService =
                mock(LevelTestQuestionPoolHealthService.class);
        LevelTestQuestionService questionService =
                mock(LevelTestQuestionService.class);
        LevelTestReferenceAudioUploadService audioUploadService =
                mock(LevelTestReferenceAudioUploadService.class);

        LanguageLearningUserSetting setting =
                mock(LanguageLearningUserSetting.class);
        when(setting.getOriginLanguage()).thenReturn("ko");
        when(setting.getLearningLanguage()).thenReturn("ja");
        when(userSettingRepository
                .findAllByOriginLanguageIsNotNullAndLearningLanguageIsNotNull())
                .thenReturn(List.of(setting));
        when(audioUploadService.requiresReferenceAudio(any(), any()))
                .thenReturn(false);
        when(poolQueryService.bucketCount(
                anyString(),
                anyString(),
                any(),
                any(),
                anyInt(),
                anyString(),
                anyString()
        )).thenReturn(0L);

        Executor directExecutor = Runnable::run;
        LevelTestQuestionPoolReplenishmentService service =
                new LevelTestQuestionPoolReplenishmentService(
                        userSettingRepository,
                        poolQueryService,
                        poolCommandService,
                        healthService,
                        new LevelTestQuestionPoolTargetPlanner(
                                new LevelTestRecipe()
                        ),
                        poolPolicy,
                        questionService,
                        audioUploadService,
                        directExecutor,
                        20,
                        5
                );

        var jobs = service.plan(1000, 20);

        assertThat(jobs).hasSize(20);
        assertThat(jobs)
                .allMatch(job -> job.pair().originLanguage().equals("ko"))
                .allMatch(job -> job.pair().learningLanguage().equals("ja"));
    }
    @Test
    void quarantinedQuestionIsRegeneratedAndLinkedAsReplacement() {
        LanguageLearningUserSettingRepository userSettingRepository =
                mock(LanguageLearningUserSettingRepository.class);
        LevelTestQuestionPoolQueryService poolQueryService =
                mock(LevelTestQuestionPoolQueryService.class);
        LevelTestQuestionPoolCommandService poolCommandService =
                mock(LevelTestQuestionPoolCommandService.class);
        LevelTestQuestionPoolHealthService healthService =
                mock(LevelTestQuestionPoolHealthService.class);
        LevelTestQuestionPoolPolicy poolPolicy =
                mock(LevelTestQuestionPoolPolicy.class);
        LevelTestQuestionService questionService =
                mock(LevelTestQuestionService.class);
        LevelTestReferenceAudioUploadService audioUploadService =
                mock(LevelTestReferenceAudioUploadService.class);

        LevelTestQuestionPool quarantined = mock(LevelTestQuestionPool.class);
        LevelTestQuestionPool replacement = mock(LevelTestQuestionPool.class);
        when(quarantined.getId()).thenReturn(30019L);
        when(quarantined.getOriginLanguage()).thenReturn("ko");
        when(quarantined.getLearningLanguage()).thenReturn("ja");
        when(quarantined.getGeneratedForQuestionNumber()).thenReturn(7);
        when(quarantined.getComplexityBand()).thenReturn(5);
        when(quarantined.getQuarantineReason()).thenReturn("READING_PASSAGE_MISSING");
        when(replacement.getId()).thenReturn(30045L);
        when(replacement.isActive()).thenReturn(true);

        when(healthService.sweep()).thenReturn(
                new LevelTestQuestionPoolHealthService.SweepResult(10, 1, 30019L)
        );
        when(poolPolicy.targetSize()).thenReturn(1000);
        when(poolQueryService.pendingRepairs(5)).thenReturn(List.of(quarantined));
        when(poolQueryService.recentContentHashes("ko", "ja")).thenReturn(List.of("bad-hash"));
        when(questionService.generateBatchPoolQuestion(
                anyString(),
                anyString(),
                anyInt(),
                anyInt(),
                anyString(),
                any()
        )).thenReturn(replacement);
        when(userSettingRepository
                .findAllByOriginLanguageIsNotNullAndLearningLanguageIsNotNull())
                .thenReturn(List.of());

        LevelTestQuestionPoolReplenishmentService service =
                new LevelTestQuestionPoolReplenishmentService(
                        userSettingRepository,
                        poolQueryService,
                        poolCommandService,
                        healthService,
                        new LevelTestQuestionPoolTargetPlanner(new LevelTestRecipe()),
                        poolPolicy,
                        questionService,
                        audioUploadService,
                        Runnable::run,
                        20,
                        5
                );

        var result = service.replenish();

        assertThat(result.quarantined()).isEqualTo(1);
        assertThat(result.repairRequested()).isEqualTo(1);
        assertThat(result.repaired()).isEqualTo(1);
        verify(poolCommandService).markReplaced(30019L, 30045L);
    }

}
