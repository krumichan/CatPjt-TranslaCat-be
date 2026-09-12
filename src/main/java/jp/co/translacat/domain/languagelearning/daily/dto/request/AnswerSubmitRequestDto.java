package jp.co.translacat.domain.languagelearning.daily.dto.request;

public record AnswerSubmitRequestDto(
        String answer,
        String contentRevision
) {
    public AnswerSubmitRequestDto(String answer) {
        this(answer, null);
    }
}
