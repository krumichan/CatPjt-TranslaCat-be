package jp.co.translacat.domain.languagelearning.daily.service;

import com.fasterxml.jackson.core.type.TypeReference;

import jp.co.translacat.domain.languagelearning.common.enums.EvaluationStatus;
import jp.co.translacat.domain.languagelearning.common.enums.WritingMetric;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.daily.dto.response.AnswerAttemptResponseDto;
import jp.co.translacat.domain.languagelearning.daily.dto.response.AnswerResultResponseDto;
import jp.co.translacat.domain.languagelearning.daily.dto.response.DailyWritingItemResponseDto;
import jp.co.translacat.domain.languagelearning.daily.dto.response.DailyWritingSetResponseDto;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingItem;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingSet;
import jp.co.translacat.domain.languagelearning.daily.entity.WritingAnswer;
import jp.co.translacat.domain.languagelearning.daily.mapper.WritingEvaluationResponseMapper;
import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingItemRepository;
import jp.co.translacat.domain.languagelearning.daily.repository.WritingAnswerRepository;
import jp.co.translacat.domain.languagelearning.daily.repository.WritingEvaluationRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DailyWritingResponseQueryService {

    private final DailyWritingItemRepository dailyWritingItemRepository;
    private final WritingAnswerRepository writingAnswerRepository;
    private final WritingEvaluationRepository writingEvaluationRepository;
    private final WritingEvaluationResponseMapper evaluationResponseMapper;
    private final LanguageLearningJsonCodec jsonCodec;

    public DailyWritingSetResponseDto toSetResponse(
            DailyWritingSet dailySet,
            LocalDate today,
            int reviewAvailableDays
    ) {
        boolean reviewAvailable = isReviewAvailable(
                dailySet.getLearningDate(),
                today,
                reviewAvailableDays
        );
        List<DailyWritingItemResponseDto> items = dailyWritingItemRepository
                .findAllByDailySetIdOrderByOrderNoAsc(dailySet.getId())
                .stream()
                .map(item -> toItemResponse(
                        item,
                        today,
                        reviewAvailable
                ))
                .toList();

        return new DailyWritingSetResponseDto(
                dailySet.getId(),
                dailySet.getLearningDate(),
                dailySet.getSnapshotId(),
                dailySet.getStatus(),
                dailySet.getSentenceCount(),
                dailySet.getRegenerationCount(),
                dailySet.getPromptVersion(),
                reviewAvailable,
                items
        );
    }

    public AnswerResultResponseDto toAnswerResult(
            WritingAnswer answer
    ) {
        return new AnswerResultResponseDto(
                answer.getId(),
                answer.getDailyItem().getId(),
                answer.getAttemptDate(),
                evaluationResponseMapper.toResponse(
                        writingEvaluationRepository
                                .findByAnswerId(answer.getId())
                                .orElse(null)
                )
        );
    }

    private DailyWritingItemResponseDto toItemResponse(
            DailyWritingItem item,
            LocalDate today,
            boolean reviewAvailable
    ) {
        List<WritingAnswer> answers = writingAnswerRepository
                .findAllByDailyItemIdOrderByAttemptDateAsc(item.getId());
        WritingAnswer todayAnswer = answers.stream()
                .filter(answer -> today.equals(answer.getAttemptDate()))
                .findFirst()
                .orElse(null);
        boolean successfulToday = isSuccessful(todayAnswer);

        return new DailyWritingItemResponseDto(
                item.getId(),
                item.getOrderNo(),
                item.getDifficulty(),
                item.getOriginText(),
                jsonCodec.read(
                        item.getKeywordsJson(),
                        new TypeReference<>() {
                        }
                ),
                jsonCodec.read(
                        item.getFocusMetricsJson(),
                        new TypeReference<>() {
                        }
                ),
                item.getFocusReason(),
                !answers.isEmpty(),
                todayAnswer != null,
                reviewAvailable && !successfulToday,
                answers.stream()
                        .map(this::toAttemptResponse)
                        .toList()
        );
    }

    private AnswerAttemptResponseDto toAttemptResponse(
            WritingAnswer answer
    ) {
        return new AnswerAttemptResponseDto(
                answer.getId(),
                answer.getAttemptDate(),
                answer.getAnswerText(),
                answer.getSubmittedAt(),
                evaluationResponseMapper.toResponse(
                        writingEvaluationRepository
                                .findByAnswerId(answer.getId())
                                .orElse(null)
                )
        );
    }

    private boolean isSuccessful(WritingAnswer answer) {
        if (answer == null) {
            return false;
        }

        return writingEvaluationRepository.findByAnswerId(answer.getId())
                .map(evaluation -> evaluation.getStatus()
                        == EvaluationStatus.SUCCESS)
                .orElse(false);
    }

    private boolean isReviewAvailable(
            LocalDate learningDate,
            LocalDate today,
            int reviewAvailableDays
    ) {
        return !today.isBefore(learningDate)
                && !today.isAfter(
                        learningDate.plusDays(reviewAvailableDays - 1L)
                );
    }
}
