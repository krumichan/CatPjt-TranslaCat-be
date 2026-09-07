package jp.co.translacat.domain.languagelearning.practice.dto.request;

import java.util.List;

public record PracticeAnswerSubmitRequestDto(
        List<String> answer
) {
}
