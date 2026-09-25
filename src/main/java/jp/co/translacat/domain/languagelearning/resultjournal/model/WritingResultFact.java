package jp.co.translacat.domain.languagelearning.resultjournal.model;

import jp.co.translacat.domain.languagelearning.ai.dto.model.SelectedKeywordDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiWritingEvaluationResponseDto;

import java.util.List;

/**
 * 평가 응답과 선택 키워드를 보존한다. 전체 학습 세션이나 Profile 상태의 복제는 아니다.
 */
public record WritingResultFact(
        String resultKind, long evaluationId, long answerId, long dailyItemId,
        String learningDate, String originLanguage, String learningLanguage, String difficulty,
        AiWritingEvaluationResponseDto response, List<SelectedKeywordDto> selectedKeywords
) {
    @Override
    public String toString() {
        return "WritingResultFact(payload=<redacted>)";
    }
}
