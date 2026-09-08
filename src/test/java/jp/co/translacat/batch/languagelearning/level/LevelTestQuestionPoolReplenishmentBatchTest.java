package jp.co.translacat.batch.languagelearning.level;

import jp.co.translacat.domain.languagelearning.level.pool.service.LevelTestQuestionPoolReplenishmentService;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningAdminSettingQueryService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LevelTestQuestionPoolReplenishmentBatchTest {

    @Mock
    private LevelTestQuestionPoolReplenishmentService replenishmentService;

    @Mock
    private LanguageLearningAdminSettingQueryService adminSettingQueryService;

    private LevelTestQuestionPoolReplenishmentBatch batch;

    @BeforeEach
    void setUp() {
        batch = new LevelTestQuestionPoolReplenishmentBatch(
                replenishmentService,
                adminSettingQueryService
        );
    }

    @Test
    void skipsWhenAdminDisabled() {
        when(adminSettingQueryService
                .isLevelTestQuestionPoolReplenishmentEnabled())
                .thenReturn(false);

        batch.replenish();

        verify(replenishmentService, never()).replenish();
    }

    @Test
    void runsWhenAdminEnabled() {
        when(adminSettingQueryService
                .isLevelTestQuestionPoolReplenishmentEnabled())
                .thenReturn(true);
        when(replenishmentService.replenish())
                .thenReturn(
                        new LevelTestQuestionPoolReplenishmentService.ReplenishmentResult(
                                1000, 0, 0, 0, 0, 0, 0, 0, 0, 0
                        )
                );

        batch.replenish();

        verify(replenishmentService).replenish();
    }
}
