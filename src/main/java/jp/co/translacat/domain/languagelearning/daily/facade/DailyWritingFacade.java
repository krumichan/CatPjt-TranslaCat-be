package jp.co.translacat.domain.languagelearning.daily.facade;

import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingType;
import jp.co.translacat.domain.languagelearning.daily.dto.request.AnswerSubmitRequestDto;
import jp.co.translacat.domain.languagelearning.daily.dto.response.AnswerResultResponseDto;
import jp.co.translacat.domain.languagelearning.daily.dto.response.DailyWritingSetResponseDto;
import jp.co.translacat.domain.languagelearning.daily.port.DailyWritingGateway;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class DailyWritingFacade {

    private final DailyWritingGateway gateway;

    public DailyWritingSetResponseDto getOrGenerateToday(Long userId, DailyWritingType writingType) {
        return gateway.create(userId, writingType);
    }

    public DailyWritingSetResponseDto getHistory(
            Long userId,
            LocalDate learningDate,
            DailyWritingType writingType
    ) {
        return gateway.findByDate(userId, learningDate, writingType)
                .orElseThrow(() -> new BusinessException("Daily Set을 찾을 수 없습니다.",
                        LanguageLearningErrorCode.DAILY_SET_NOT_FOUND));
    }

    public DailyWritingSetResponseDto retryGeneration(Long userId, Long dailySetId) {
        return gateway.retry(userId, dailySetId);
    }

    public DailyWritingSetResponseDto regenerateUnanswered(
            Long userId,
            Long dailySetId
    ) {
        return gateway.regenerate(userId, dailySetId);
    }

    public void resumeEvaluation(
            Long userId,
            Long itemId
    ) {
        gateway.resume(userId, itemId);
    }

    public AnswerResultResponseDto submitAnswer(
            Long userId,
            Long itemId,
            AnswerSubmitRequestDto request
    ) {
        return gateway.submit(userId, itemId, request);
    }
}
