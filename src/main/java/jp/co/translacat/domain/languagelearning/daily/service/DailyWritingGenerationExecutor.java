package jp.co.translacat.domain.languagelearning.daily.service;

import jp.co.translacat.domain.languagelearning.ai.dto.response.AiDailyWritingGenerationResponseDto;
import jp.co.translacat.domain.languagelearning.ai.port.LanguageLearningAiClient;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingSet;
import jp.co.translacat.domain.languagelearning.daily.factory.DailyWritingGenerationRequestFactory;
import jp.co.translacat.domain.languagelearning.daily.model.DailyWritingSnapshot;
import jp.co.translacat.domain.languagelearning.daily.validator.DailyWritingGenerationResponseValidator;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DailyWritingGenerationExecutor {

    private static final int FAILURE_MESSAGE_MAX_LENGTH = 1000;

    private final LanguageLearningAiClient aiClient;
    private final DailyWritingGenerationStateCommandService stateCommandService;
    private final DailyWritingSnapshotService snapshotService;
    private final DailyWritingGenerationRequestFactory requestFactory;
    private final DailyWritingGenerationResponseValidator responseValidator;

    public DailyWritingSet execute(
            DailyWritingSet dailySet,
            DailyWritingSnapshot snapshot
    ) {
        Long dailySetId = dailySet.getId();
        stateCommandService.markGenerating(
                dailySetId,
                snapshotService.write(snapshot)
        );

        try {
            AiDailyWritingGenerationResponseDto response =
                    aiClient.generateDaily(
                            requestFactory.createInitial(dailySet.getUser().getId(), dailySet, snapshot)
                    );

            responseValidator.validate(
                    response,
                    snapshot.sentenceCount(),
                    snapshot.difficultyDistribution(),
                    dailySet.getWritingType()
            );

            return stateCommandService.complete(
                    dailySetId,
                    snapshot.learningLanguage(),
                    response.items(),
                    response.promptVersion()
            );
        } catch (BusinessException e) {
            stateCommandService.fail(dailySetId, trimMessage(e.getMessage()));
            throw e;
        } catch (Exception e) {
            stateCommandService.fail(
                    dailySetId,
                    trimMessage(e.getMessage())
            );
            throw new BusinessException(
                    "Daily Writing 문제 생성에 실패했습니다.",
                    LanguageLearningErrorCode.DAILY_SET_GENERATION_FAILED
            );
        }
    }

    private String trimMessage(String message) {
        if (message == null) {
            return "unknown";
        }

        return message.length() <= FAILURE_MESSAGE_MAX_LENGTH
                ? message
                : message.substring(0, FAILURE_MESSAGE_MAX_LENGTH);
    }
}
