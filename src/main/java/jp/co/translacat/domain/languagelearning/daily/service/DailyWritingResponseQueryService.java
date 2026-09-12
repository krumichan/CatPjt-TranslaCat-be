package jp.co.translacat.domain.languagelearning.daily.service;

import com.fasterxml.jackson.core.type.TypeReference;

import jp.co.translacat.domain.languagelearning.common.enums.EvaluationStatus;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.daily.dto.response.AnswerAttemptResponseDto;
import jp.co.translacat.domain.languagelearning.daily.dto.response.AnswerResultResponseDto;
import jp.co.translacat.domain.languagelearning.daily.dto.response.DailyWritingItemResponseDto;
import jp.co.translacat.domain.languagelearning.daily.dto.response.DailyWritingSetResponseDto;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingItem;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingSet;
import jp.co.translacat.domain.languagelearning.daily.entity.WritingAnswer;
import jp.co.translacat.domain.languagelearning.daily.entity.WritingEvaluation;
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
    private final DailyWritingItemRevisionService itemRevisionService;

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
        boolean regenerating = dailySet.isRegenerationActive(
                java.time.LocalDateTime.now()
        );
        List<DailyWritingItemResponseDto> items = dailyWritingItemRepository
                .findAllByDailySetIdOrderByOrderNoAsc(dailySet.getId())
                .stream()
                .map(item -> toItemResponse(
                        item,
                        today,
                        reviewAvailable,
                        regenerating
                ))
                .toList();

        return new DailyWritingSetResponseDto(
                dailySet.getId(),
                dailySet.getLearningDate(),
                dailySet.getWritingType(),
                dailySet.getSnapshotId(),
                dailySet.getStatus(),
                dailySet.getSentenceCount(),
                items.size(),
                dailySet.getFailureMessage(),
                dailySet.getRegenerationCount(),
                dailySet.getPromptVersion(),
                reviewAvailable,
                items,
                regenerating
        );
    }

    public AnswerResultResponseDto toAnswerResult(
            WritingAnswer answer
    ) {
        WritingEvaluation evaluation = writingEvaluationRepository
                .findByAnswerId(answer.getId())
                .orElse(null);
        return new AnswerResultResponseDto(
                answer.getId(),
                answer.getDailyItem().getId(),
                answer.getAttemptDate(),
                evaluation == null ? null : evaluation.getStatus(),
                evaluation == null ? null : evaluation.getFailureMessage(),
                evaluationResponseMapper.toResponse(evaluation)
        );
    }

    private DailyWritingItemResponseDto toItemResponse(
            DailyWritingItem item,
            LocalDate today,
            boolean reviewAvailable,
            boolean regenerating
    ) {
        List<WritingAnswer> answers = writingAnswerRepository
                .findAllByDailyItemIdOrderByAttemptDateAsc(item.getId());
        WritingAnswer todayAnswer = answers.stream()
                .filter(answer -> today.equals(answer.getAttemptDate()))
                .findFirst()
                .orElse(null);
        EvaluationStatus todayStatus = resolveStatus(todayAnswer);
        boolean successfulToday = todayStatus == EvaluationStatus.SUCCESS;
        boolean evaluatingToday = todayStatus == EvaluationStatus.PENDING;

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
                readStringList(item.getProvidedFactsJson()),
                readStringList(item.getRequiredIntentsJson()),
                readStringList(item.getResponseConstraintsJson()),
                !answers.isEmpty(),
                todayAnswer != null,
                reviewAvailable
                        && !successfulToday
                        && !evaluatingToday
                        && !regenerating,
                answers.stream()
                        .map(this::toAttemptResponse)
                        .toList(),
                itemRevisionService.revision(item)
        );
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return jsonCodec.read(
                json,
                new TypeReference<>() {
                }
        );
    }

    private AnswerAttemptResponseDto toAttemptResponse(
            WritingAnswer answer
    ) {
        WritingEvaluation evaluation = writingEvaluationRepository
                .findByAnswerId(answer.getId())
                .orElse(null);
        return new AnswerAttemptResponseDto(
                answer.getId(),
                answer.getAttemptDate(),
                answer.getAnswerText(),
                answer.getSubmittedAt(),
                evaluation == null ? null : evaluation.getStatus(),
                evaluation == null ? null : evaluation.getFailureMessage(),
                evaluationResponseMapper.toResponse(evaluation)
        );
    }

    private EvaluationStatus resolveStatus(WritingAnswer answer) {
        if (answer == null) {
            return null;
        }
        return writingEvaluationRepository.findByAnswerId(answer.getId())
                .map(WritingEvaluation::getStatus)
                .orElse(null);
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
