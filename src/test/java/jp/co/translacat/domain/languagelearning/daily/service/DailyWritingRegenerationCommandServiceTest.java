package jp.co.translacat.domain.languagelearning.daily.service;

import jp.co.translacat.domain.languagelearning.ai.dto.model.DifficultyDistributionDto;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiDailyWritingGenerationRequestDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiDailyWritingGenerationResponseDto;
import jp.co.translacat.domain.languagelearning.ai.port.LanguageLearningAiClient;
import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingDifficulty;
import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingType;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingSet;
import jp.co.translacat.domain.languagelearning.daily.service.DailyWritingRegenerationStateCommandService.RegenerationClaim;
import jp.co.translacat.domain.languagelearning.daily.service.DailyWritingRegenerationStateCommandService.RegenerationTarget;
import jp.co.translacat.domain.languagelearning.daily.validator.DailyWritingGenerationResponseValidator;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningAdminSetting;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningAdminSettingQueryService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyWritingRegenerationCommandServiceTest {

    @Mock
    private DailyWritingRegenerationStateCommandService stateCommandService;
    @Mock
    private LanguageLearningAiClient aiClient;
    @Mock
    private DailyWritingGenerationResponseValidator responseValidator;
    @Mock
    private LanguageLearningAdminSettingQueryService adminSettingQueryService;
    @Mock
    private LanguageLearningAdminSetting adminSetting;
    @Mock
    private AiDailyWritingGenerationRequestDto request;
    @Mock
    private AiDailyWritingGenerationResponseDto response;
    @Mock
    private DailyWritingSet dailySet;

    private DailyWritingRegenerationCommandService service;

    @BeforeEach
    void setUp() {
        service = new DailyWritingRegenerationCommandService(
                stateCommandService,
                aiClient,
                responseValidator,
                adminSettingQueryService
        );
        when(adminSettingQueryService.getOrCreateEntity())
                .thenReturn(adminSetting);
        when(adminSetting.isAdaptiveWritingEnabled())
                .thenReturn(true);
    }

    @Test
    void aiCallRunsBetweenShortClaimAndPublishSteps() {
        RegenerationClaim claim = claim();
        when(stateCommandService.claim(10L, 20L))
                .thenReturn(claim);
        when(aiClient.generateDaily(request)).thenReturn(response);
        when(stateCommandService.publish(claim, response))
                .thenReturn(dailySet);

        DailyWritingSet result = service.regenerateUnanswered(
                10L,
                20L
        );

        assertThat(result).isSameAs(dailySet);
        InOrder ordered = inOrder(
                stateCommandService,
                aiClient,
                responseValidator
        );
        ordered.verify(stateCommandService).claim(10L, 20L);
        ordered.verify(aiClient).generateDaily(request);
        ordered.verify(responseValidator).validate(
                response,
                1,
                claim.distribution(),
                DailyWritingType.TRANSLATION
        );
        ordered.verify(stateCommandService).publish(claim, response);
    }

    @Test
    void aiFailureReleasesClaim() {
        RegenerationClaim claim = claim();
        when(stateCommandService.claim(10L, 20L))
                .thenReturn(claim);
        when(aiClient.generateDaily(request))
                .thenThrow(new IllegalStateException("ai failed"));

        assertThatThrownBy(() -> service.regenerateUnanswered(
                10L,
                20L
        )).isInstanceOf(IllegalStateException.class);

        verify(stateCommandService).release(20L, "token");
    }

    private RegenerationClaim claim() {
        DifficultyDistributionDto distribution =
                new DifficultyDistributionDto(0, 1, 0);
        return new RegenerationClaim(
                20L,
                "token",
                request,
                List.of(new RegenerationTarget(
                        30L,
                        2,
                        DailyWritingDifficulty.NORMAL,
                        "revision"
                )),
                distribution,
                DailyWritingType.TRANSLATION,
                "ja"
        );
    }
}
