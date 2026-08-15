package jp.co.translacat.domain.languagelearning.setting.service;

import jp.co.translacat.domain.languagelearning.setting.audit.service.LanguageLearningAdminSettingAuditCommandService;
import jp.co.translacat.domain.languagelearning.setting.dto.request.AdminSettingUpdateRequestDto;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningAdminSetting;
import jp.co.translacat.domain.languagelearning.setting.repository.LanguageLearningAdminSettingRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LanguageLearningAdminSettingCommandServiceTest {

    @Mock
    private LanguageLearningAdminSettingRepository repository;

    @Mock
    private LanguageLearningAdminSettingAuditCommandService auditCommandService;

    private LanguageLearningAdminSettingQueryService queryService;
    private LanguageLearningAdminSettingCommandService commandService;

    @BeforeEach
    void setUp() {
        queryService = new LanguageLearningAdminSettingQueryService(repository);
        commandService = new LanguageLearningAdminSettingCommandService(
                queryService,
                auditCommandService
        );
    }

    @Test
    void updatesAdminControlledLimits() {
        LanguageLearningAdminSetting setting =
                LanguageLearningAdminSetting.createDefault();
        when(repository.findById(LanguageLearningAdminSetting.DEFAULT_ID))
                .thenReturn(Optional.of(setting));

        LanguageLearningAdminSetting updated = commandService.update(
                new AdminSettingUpdateRequestDto(
                        6,
                        2,
                        12,
                        4,
                        14,
                        45,
                        true,
                        true
                )
        );

        assertThat(updated.getDefaultDailySentenceCount()).isEqualTo(6);
        assertThat(updated.getMinDailySentenceCount()).isEqualTo(2);
        assertThat(updated.getMaxDailySentenceCount()).isEqualTo(12);
        assertThat(updated.getReviewAvailableDays()).isEqualTo(14);
    }
    @Test
    void recordsAdminSettingChangeAudit() {
        LanguageLearningAdminSetting setting =
                LanguageLearningAdminSetting.createDefault();
        when(repository.findById(LanguageLearningAdminSetting.DEFAULT_ID))
                .thenReturn(Optional.of(setting));

        commandService.update(
                7L,
                new AdminSettingUpdateRequestDto(
                        6,
                        2,
                        12,
                        4,
                        14,
                        45,
                        true,
                        true
                )
        );

        verify(auditCommandService).record(
                eq(7L),
                any(),
                any()
        );
    }

}
