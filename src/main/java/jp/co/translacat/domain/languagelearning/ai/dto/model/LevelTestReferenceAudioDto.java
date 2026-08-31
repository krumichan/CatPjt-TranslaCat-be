package jp.co.translacat.domain.languagelearning.ai.dto.model;

public record LevelTestReferenceAudioDto(
        String objectKey,
        String contentType,
        Integer durationMs,
        String checksumSha256
) {
}
