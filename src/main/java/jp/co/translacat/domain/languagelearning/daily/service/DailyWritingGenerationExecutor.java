package jp.co.translacat.domain.languagelearning.daily.service;

import jp.co.translacat.domain.languagelearning.ai.dto.response.AiDailyWritingGenerationResponseDto;
import jp.co.translacat.domain.languagelearning.ai.port.LanguageLearningAiClient;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingSet;
import jp.co.translacat.domain.languagelearning.daily.factory.DailyWritingGenerationRequestFactory;
import jp.co.translacat.domain.languagelearning.daily.model.DailyWritingSnapshot;
import jp.co.translacat.domain.languagelearning.daily.validator.DailyWritingGenerationResponseValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class DailyWritingGenerationExecutor {

    private static final int FAILURE_MESSAGE_MAX_LENGTH = 1000;

    private final LanguageLearningAiClient aiClient;
    private final DailyWritingGenerationStateCommandService stateCommandService;
    private final DailyWritingSnapshotService snapshotService;
    private final DailyWritingGenerationRequestFactory requestFactory;
    private final DailyWritingGenerationResponseValidator responseValidator;

    @Async("writingGenerationExecutor")
    public CompletableFuture<Void> execute(Long dailySetId) {
        while (true) {
            var claim = stateCommandService.claim(dailySetId);
            if (claim == null) {
                return CompletableFuture.completedFuture(null);
            }
            DailyWritingSet dailySet = claim.dailySet();
            try {
                DailyWritingSnapshot snapshot = snapshotService.read(dailySet);
                AiDailyWritingGenerationResponseDto response = aiClient.generateDaily(
                        requestFactory.createItem(dailySet, snapshot, claim.order(), claim.token()));
                responseValidator.validate(
                        response, 1, requestFactory.distributionForItem(snapshot, claim.order()),
                        dailySet.getWritingType());
                if (!stateCommandService.publish(
                        dailySetId, claim.token(), claim.order(), snapshot.learningLanguage(),
                        response.items().getFirst(), response.promptVersion())) {
                    return CompletableFuture.completedFuture(null);
                }
            } catch (Exception e) {
                log.warn("Writing item generation failed. dailySetId={} order={}", dailySetId, claim.order(), e);
                stateCommandService.fail(dailySetId, claim.token(), trimMessage(e.getMessage()));
                return CompletableFuture.completedFuture(null);
            }
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
