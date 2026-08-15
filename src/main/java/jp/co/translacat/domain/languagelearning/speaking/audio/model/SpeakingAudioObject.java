package jp.co.translacat.domain.languagelearning.speaking.audio.model;

public record SpeakingAudioObject(
        String objectKey,
        byte[] bytes,
        String contentType
) {
}
