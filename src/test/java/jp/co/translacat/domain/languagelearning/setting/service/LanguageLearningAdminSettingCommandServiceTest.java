package jp.co.translacat.domain.languagelearning.setting.service;

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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LanguageLearningAdminSettingCommandServiceTest {

    @Mock
    private LanguageLearningAdminSettingRepository repository;

    private LanguageLearningAdminSettingQueryService queryService;
    private LanguageLearningAdminSettingCommandService commandService;

    @BeforeEach
    void setUp() {
        queryService = new LanguageLearningAdminSettingQueryService(repository);
        commandService = new LanguageLearningAdminSettingCommandService(
                queryService
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
}
