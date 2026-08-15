package jp.co.translacat.domain.languagelearning.speaking.audio.service;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SpeakingAudioKeyFactory {

    public String userTurn(
            Long userId,
            Long sessionId,
            Long turnId,
            String extension
    ) {
        return build(
                userId,
                sessionId,
                "user-" + turnId,
                extension
        );
    }

    public String assistantTurn(
            Long userId,
            Long sessionId,
            String suffix
    ) {
        return build(
                userId,
                sessionId,
                "assistant-" + suffix,
                "wav"
        );
    }

    private String build(
            Long userId,
            Long sessionId,
            String filePrefix,
            String extension
    ) {
        String safeExtension = extension == null || extension.isBlank()
                ? "bin"
                : extension.replaceAll("[^a-zA-Z0-9]", "");

        return "language-learning/speaking/"
                + userId
                + "/"
                + sessionId
                + "/"
                + filePrefix
                + "-"
                + UUID.randomUUID()
                + "."
                + safeExtension;
    }
}
