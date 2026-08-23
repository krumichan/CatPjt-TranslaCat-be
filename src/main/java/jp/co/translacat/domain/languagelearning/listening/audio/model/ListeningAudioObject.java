package jp.co.translacat.domain.languagelearning.listening.audio.model;

public record ListeningAudioObject(
        String objectKey,
        byte[] bytes,
        String contentType
) {
}
