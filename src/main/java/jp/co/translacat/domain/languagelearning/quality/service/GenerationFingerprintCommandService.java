package jp.co.translacat.domain.languagelearning.quality.service;

import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.quality.common.LanguageLearningContentSource;
import jp.co.translacat.domain.languagelearning.quality.dto.DiversityMetadata;
import jp.co.translacat.domain.languagelearning.quality.entity.LanguageLearningGenerationFingerprint;
import jp.co.translacat.domain.languagelearning.quality.repository.LanguageLearningGenerationFingerprintRepository;
import jp.co.translacat.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GenerationFingerprintCommandService {

    public static final String POLICY_VERSION =
            "language-learning-diversity-v1";

    private final LanguageLearningGenerationFingerprintRepository repository;
    private final UserRepository userRepository;
    private final LanguageLearningJsonCodec jsonCodec;

    @Transactional
    public void register(
            Long userId,
            LanguageLearningContentSource sourceType,
            String sourceId,
            String learningLanguage,
            String content,
            DiversityMetadata metadata
    ) {
        if (content == null || content.isBlank()) {
            return;
        }

        String hash = resolveHash(content, metadata);
        if (repository
                .existsByUserIdAndLearningLanguageAndContentHashAndGeneratedAtGreaterThanEqual(
                        userId,
                        learningLanguage,
                        hash,
                        LocalDateTime.now().minusDays(90)
                )) {
            return;
        }

        var user = userRepository.findById(userId).orElseThrow();
        repository.save(LanguageLearningGenerationFingerprint.create(
                user,
                sourceType,
                sourceId,
                learningLanguage,
                LocalDateTime.now(),
                hash,
                metadata == null ? null : metadata.similarityKey(),
                excerpt(content),
                metadata == null ? null : metadata.scenarioCategory(),
                metadata == null ? null : metadata.communicativeIntent(),
                metadata == null ? null : metadata.taskArchetype(),
                jsonCodec.write(
                        metadata == null
                                || metadata.grammarFocusCodes() == null
                                ? List.of()
                                : metadata.grammarFocusCodes()
                ),
                metadata == null ? null : metadata.semanticSummary(),
                POLICY_VERSION
        ));
    }

    private String resolveHash(
            String content,
            DiversityMetadata metadata
    ) {
        if (metadata != null
                && metadata.contentHash() != null
                && !metadata.contentHash().isBlank()) {
            return metadata.contentHash();
        }
        return sha256(content);
    }

    private String excerpt(String text) {
        String value = text.strip();
        return value.length() <= 500
                ? value
                : value.substring(0, 500);
    }

    private String sha256(String text) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            text.strip().getBytes(StandardCharsets.UTF_8)
                    )
            );
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
