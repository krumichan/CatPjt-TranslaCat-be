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
    private final DailyWritingItemCommandService itemCommandService;
    private final DailyWritingSnapshotService snapshotService;
    private final DailyWritingGenerationRequestFactory requestFactory;
    private final DailyWritingGenerationResponseValidator responseValidator;

    public DailyWritingSet execute(
            DailyWritingSet dailySet,
            DailyWritingSnapshot snapshot
    ) {
        dailySet.restartGeneration(snapshotService.write(snapshot));

        try {
            AiDailyWritingGenerationResponseDto response =
                    aiClient.generateDaily(
                            requestFactory.createInitial(snapshot)
                    );

            responseValidator.validate(
                    response,
                    snapshot.sentenceCount(),
                    snapshot.difficultyDistribution()
            );

            itemCommandService.createAll(
                    dailySet,
                    response.items()
            );
            dailySet.ready(response.promptVersion());

            return dailySet;
        } catch (BusinessException e) {
            dailySet.fail(e.getMessage());
            throw e;
        } catch (Exception e) {
            dailySet.fail(trimMessage(e.getMessage()));
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
