package jp.co.translacat.domain.languagelearning.daily.facade;

import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingType;
import jp.co.translacat.domain.languagelearning.daily.dto.request.AnswerSubmitRequestDto;
import jp.co.translacat.domain.languagelearning.daily.dto.response.AnswerResultResponseDto;
import jp.co.translacat.domain.languagelearning.daily.dto.response.DailyWritingSetResponseDto;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingSet;
import jp.co.translacat.domain.languagelearning.daily.entity.WritingAnswer;
import jp.co.translacat.domain.languagelearning.daily.service.DailyWritingGenerationCommandService;
import jp.co.translacat.domain.languagelearning.daily.service.DailyWritingQueryService;
import jp.co.translacat.domain.languagelearning.daily.service.DailyWritingRegenerationCommandService;
import jp.co.translacat.domain.languagelearning.daily.service.WritingAnswerCommandService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class DailyWritingFacade {

    private final DailyWritingGenerationCommandService dailyWritingGenerationCommandService;
    private final DailyWritingRegenerationCommandService dailyWritingRegenerationCommandService;
    private final WritingAnswerCommandService writingAnswerCommandService;
    private final DailyWritingQueryService dailyWritingQueryService;

    public DailyWritingSetResponseDto getOrGenerateToday(Long userId, DailyWritingType writingType) {
        DailyWritingSet dailySet =
                dailyWritingGenerationCommandService.getOrGenerateToday(userId, writingType);

        return dailyWritingQueryService.toResponse(userId, dailySet);
    }

    public DailyWritingSetResponseDto getHistory(
            Long userId,
            LocalDate learningDate,
            DailyWritingType writingType
    ) {
        return dailyWritingQueryService.getByDate(userId, learningDate, writingType);
    }

    public DailyWritingSetResponseDto regenerateUnanswered(
            Long userId,
            Long dailySetId
    ) {
        DailyWritingSet dailySet =
                dailyWritingRegenerationCommandService.regenerateUnanswered(
                        userId,
                        dailySetId
                );

        return dailyWritingQueryService.toResponse(userId, dailySet);
    }

    public void resumeEvaluation(
            Long userId,
            Long itemId
    ) {
        writingAnswerCommandService.resumeEvaluation(userId, itemId);
    }

    public AnswerResultResponseDto submitAnswer(
            Long userId,
            Long itemId,
            AnswerSubmitRequestDto request
    ) {
        WritingAnswer answer = writingAnswerCommandService.submit(
                userId,
                itemId,
                request
        );

        return dailyWritingQueryService.getAnswerResult(userId, answer.getId());
    }
}
