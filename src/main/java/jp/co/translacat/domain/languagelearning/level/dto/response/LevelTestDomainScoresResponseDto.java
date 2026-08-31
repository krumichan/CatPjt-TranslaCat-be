package jp.co.translacat.domain.languagelearning.level.dto.response;

public record LevelTestDomainScoresResponseDto(
        Integer vocabulary,
        Integer grammar,
        Integer reading,
        Integer listening,
        Integer writing,
        Integer speaking
) {
}
