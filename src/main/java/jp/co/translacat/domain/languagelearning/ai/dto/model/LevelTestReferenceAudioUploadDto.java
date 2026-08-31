package jp.co.translacat.domain.languagelearning.ai.dto.model;

public record LevelTestReferenceAudioUploadDto(
        String uploadUrl,
        String objectKey,
        String contentType,
        String voice,
        String playbackSpeed
) {
}
