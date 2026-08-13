package jp.co.translacat.domain.languagelearning.setting.service;

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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LanguageLearningAdminSettingQueryServiceTest {

    @Mock
    private LanguageLearningAdminSettingRepository repository;

    private LanguageLearningAdminSettingQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new LanguageLearningAdminSettingQueryService(repository);
    }

    @Test
    void createsDocs24DefaultsWhenMissing() {
        when(repository.findById(LanguageLearningAdminSetting.DEFAULT_ID))
                .thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        LanguageLearningAdminSetting setting =
                queryService.getOrCreateEntity();

        assertThat(setting.getDefaultDailySentenceCount()).isEqualTo(5);
        assertThat(setting.getDailyKeywordMaxCount()).isEqualTo(5);
        assertThat(setting.getReviewAvailableDays()).isEqualTo(7);
    }
}
