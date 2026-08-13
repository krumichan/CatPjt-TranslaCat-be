package jp.co.translacat.domain.languagelearning.daily.model;

import jp.co.translacat.domain.languagelearning.ai.dto.model.SelectedKeywordDto;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiWritingEvaluationRequestDto;

import java.util.List;

public record WritingEvaluationRequestContext(
        AiWritingEvaluationRequestDto request,
        List<SelectedKeywordDto> relevantKeywords
) {
}
