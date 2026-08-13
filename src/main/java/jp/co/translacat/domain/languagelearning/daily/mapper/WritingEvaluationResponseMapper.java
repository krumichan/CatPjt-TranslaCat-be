package jp.co.translacat.domain.languagelearning.daily.mapper;

import com.fasterxml.jackson.core.type.TypeReference;

import jp.co.translacat.domain.languagelearning.ai.dto.model.BilingualMessageDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.WritingCorrectionDto;
import jp.co.translacat.domain.languagelearning.common.enums.EvaluationStatus;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.daily.dto.response.WritingEvaluationResponseDto;
import jp.co.translacat.domain.languagelearning.daily.entity.WritingEvaluation;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class WritingEvaluationResponseMapper {

    private final LanguageLearningJsonCodec jsonCodec;

    public WritingEvaluationResponseDto toResponse(
            WritingEvaluation evaluation
    ) {
        if (evaluation == null
                || evaluation.getStatus() != EvaluationStatus.SUCCESS) {
            return null;
        }

        return new WritingEvaluationResponseDto(
                evaluation.getId(),
                evaluation.getContext(),
                evaluation.getOverallScore(),
                evaluation.getMeaningScore(),
                evaluation.getGrammarScore(),
                evaluation.getVocabularyScore(),
                evaluation.getNaturalnessScore(),
                evaluation.getExpressionScore(),
                jsonCodec.read(
                        evaluation.getStrengthsJson(),
                        new TypeReference<>() {
                        }
                ),
                jsonCodec.read(
                        evaluation.getWeaknessesJson(),
                        new TypeReference<>() {
                        }
                ),
                jsonCodec.read(
                        evaluation.getCorrectionsJson(),
                        new TypeReference<>() {
                        }
                ),
                jsonCodec.read(
                        evaluation.getRecommendedAnswersJson(),
                        new TypeReference<>() {
                        }
                ),
                jsonCodec.read(
                        evaluation.getExplanationJson(),
                        BilingualMessageDto.class
                ),
                evaluation.getEvaluationRubricVersion(),
                evaluation.getScoringPolicyVersion(),
                evaluation.getPromptVersion(),
                evaluation.getEvaluatedAt()
        );
    }
}
