package jp.co.translacat.domain.languagelearning.daily.service;

import jp.co.translacat.domain.languagelearning.ai.dto.response.AiDailyWritingGenerationResponseDto;
import jp.co.translacat.domain.languagelearning.ai.port.LanguageLearningAiClient;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingSet;
import jp.co.translacat.domain.languagelearning.daily.service.DailyWritingRegenerationStateCommandService.RegenerationClaim;
import jp.co.translacat.domain.languagelearning.daily.validator.DailyWritingGenerationResponseValidator;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningAdminSettingQueryService;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyWritingRegenerationCommandService {

    private final DailyWritingRegenerationStateCommandService stateCommandService;
    private final LanguageLearningAiClient aiClient;
    private final DailyWritingGenerationResponseValidator responseValidator;
    private final LanguageLearningAdminSettingQueryService adminSettingQueryService;

    public DailyWritingSet regenerateUnanswered(
            Long userId,
            Long dailySetId
    ) {
        validateAdaptiveWritingEnabled();
        RegenerationClaim claim = stateCommandService.claim(
                userId,
                dailySetId
        );
        log.info(
                "Writing regeneration claimed. dailySetId={} requestId={} targetCount={}",
                dailySetId,
                claim.request().requestId(),
                claim.expectedCount()
        );

        try {
            AiDailyWritingGenerationResponseDto response =
                    aiClient.generateDaily(claim.request());
            responseValidator.validate(
                    response,
                    claim.expectedCount(),
                    claim.distribution(),
                    claim.writingType()
            );
            DailyWritingSet result = stateCommandService.publish(
                    claim,
                    response
            );
            log.info(
                    "Writing regeneration published. dailySetId={} requestId={} targetCount={}",
                    dailySetId,
                    claim.request().requestId(),
                    claim.expectedCount()
            );
            return result;
        } catch (RuntimeException exception) {
            try {
                stateCommandService.release(
                        claim.dailySetId(),
                        claim.token()
                );
            } catch (RuntimeException releaseException) {
                exception.addSuppressed(releaseException);
                log.error(
                        "Writing regeneration claim release failed. dailySetId={} requestId={}",
                        dailySetId,
                        claim.request().requestId(),
                        releaseException
                );
            }
            String errorCode = exception instanceof BusinessException businessException
                    ? businessException.getErrorCode()
                    : null;
            log.warn(
                    "Writing regeneration failed. dailySetId={} requestId={} errorType={} errorCode={}",
                    dailySetId,
                    claim.request().requestId(),
                    exception.getClass().getSimpleName(),
                    errorCode,
                    exception
            );
            throw exception;
        }
    }

    private void validateAdaptiveWritingEnabled() {
        if (!adminSettingQueryService
                .getOrCreateEntity()
                .isAdaptiveWritingEnabled()) {
            throw new BusinessException(
                    "Adaptive Writing이 비활성화되어 있습니다.",
                    LanguageLearningErrorCode.SETTING_INVALID
            );
        }
    }
}
