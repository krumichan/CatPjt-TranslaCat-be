package jp.co.translacat.domain.languagelearning.listening.audio.service;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ListeningAudioKeyFactory {

    public String reference(
            Long userId,
            Long dailySetId,
            Long itemId,
            String extension
    ) {
        return build(
                userId,
                dailySetId,
                "reference-" + itemId,
                extension
        );
    }

    public String response(
            Long userId,
            Long sessionId,
            Long responseId,
            String extension
    ) {
        return build(
                userId,
                sessionId,
                "response-" + responseId,
                extension
        );
    }

    private String build(
            Long userId,
            Long groupId,
            String prefix,
            String extension
    ) {
        String safe = extension == null || extension.isBlank()
                ? "bin"
                : extension.replaceAll("[^a-zA-Z0-9]", "");

        return "language-learning/listening/"
                + userId + "/" + groupId + "/" + prefix + "-"
                + UUID.randomUUID() + "." + safe;
    }
}
