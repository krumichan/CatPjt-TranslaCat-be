package jp.co.translacat.domain.languagelearning.level.service;

import com.fasterxml.jackson.core.type.TypeReference;

import jp.co.translacat.domain.languagelearning.ai.dto.request.AiLevelTestQuestionRequestDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiLevelTestQuestionResponseDto;
import jp.co.translacat.domain.languagelearning.ai.port.LanguageLearningAiClient;
import jp.co.translacat.domain.languagelearning.common.enums.WritingMetric;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelQuestionResponseDto;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestItem;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestSession;
import jp.co.translacat.domain.languagelearning.level.repository.LevelTestItemRepository;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningUserSetting;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LevelTestQuestionService {

    private final LevelTestItemRepository itemRepository;
    private final LanguageLearningAiClient aiClient;
    private final LevelTestQueryService queryService;
    private final LanguageLearningJsonCodec jsonCodec;

    @Transactional
    public LevelTestItem getOrGenerateCurrent(
            LevelTestSession session,
            LanguageLearningUserSetting setting
    ) {
        int questionNumber = queryService.nextQuestionNumber(
                session.getId()
        );

        if (questionNumber > session.getTotalQuestions()) {
            throw new BusinessException(
                    "Level Test가 이미 완료 단계입니다.",
                    LanguageLearningErrorCode.LEVEL_TEST_INVALID_STATE
            );
        }

        return itemRepository
                .findBySessionIdAndQuestionNumber(
                        session.getId(),
                        questionNumber
                )
                .orElseGet(() -> generate(
                        session,
                        questionNumber,
                        setting
                ));
    }

    @Transactional
    public LevelTestItem generate(
            LevelTestSession session,
            int questionNumber,
            LanguageLearningUserSetting setting
    ) {
        AiLevelTestQuestionRequestDto request =
                new AiLevelTestQuestionRequestDto(
                        "level-question-"
                                + session.getId()
                                + "-"
                                + questionNumber,
                        setting.getOriginLanguage(),
                        setting.getLearningLanguage(),
                        questionNumber,
                        session.getTotalQuestions(),
                        queryService.previousEvaluations(session.getId())
                );
        AiLevelTestQuestionResponseDto response =
                aiClient.generateLevelTestQuestion(request);

        validateResponse(response, questionNumber);

        return itemRepository.save(LevelTestItem.create(
                session,
                questionNumber,
                response.difficulty(),
                response.originText(),
                jsonCodec.write(response.focusMetrics()),
                response.focusReason(),
                response.promptVersion()
        ));
    }

    public LevelQuestionResponseDto toResponse(LevelTestItem item) {
        return new LevelQuestionResponseDto(
                item.getSession().getId(),
                item.getSession().getSessionType(),
                item.getQuestionNumber(),
                item.getSession().getTotalQuestions(),
                item.getDifficulty(),
                item.getOriginText(),
                jsonCodec.read(
                        item.getFocusMetricsJson(),
                        new TypeReference<List<WritingMetric>>() {
                        }
                ),
                item.getFocusReason(),
                item.getPromptVersion()
        );
    }

    private void validateResponse(
            AiLevelTestQuestionResponseDto response,
            int questionNumber
    ) {
        if (response == null
                || response.questionNumber() != questionNumber
                || response.difficulty() == null
                || response.originText() == null
                || response.originText().isBlank()
                || response.focusMetrics() == null
                || response.focusMetrics().isEmpty()) {
            throw new BusinessException(
                    "AI Level Test 문제 생성 결과가 유효하지 않습니다.",
                    LanguageLearningErrorCode.DAILY_SET_GENERATION_FAILED
            );
        }
    }
}
