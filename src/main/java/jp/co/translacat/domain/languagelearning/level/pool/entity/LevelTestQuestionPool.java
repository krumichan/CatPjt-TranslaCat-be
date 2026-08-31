package jp.co.translacat.domain.languagelearning.level.pool.entity;

import jakarta.persistence.*;

import jp.co.translacat.domain.languagelearning.ai.dto.response.AiLevelTestQuestionResponseDto;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestAnswerMode;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestDomain;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestItemType;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Table(
        name = "language_learning_level_test_question_pool",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_ll_level_pool_content",
                        columnNames = {
                                "origin_language",
                                "learning_language",
                                "content_hash"
                        }
                ),
                @UniqueConstraint(
                        name = "uk_ll_level_pool_similarity",
                        columnNames = {
                                "origin_language",
                                "learning_language",
                                "similarity_key"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_ll_level_pool_lookup",
                        columnList = "active,origin_language,learning_language,domain,item_type,complexity_band,policy_version,model_config_version"
                ),
                @Index(
                        name = "idx_ll_level_pool_usage",
                        columnList = "usage_count,last_used_at"
                ),
                @Index(
                        name = "idx_ll_level_pool_quarantine",
                        columnList = "active,quarantine_reason,replacement_pool_question_id,quarantined_at"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LevelTestQuestionPool extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "origin_language", nullable = false, length = 20)
    private String originLanguage;

    @Column(name = "learning_language", nullable = false, length = 20)
    private String learningLanguage;

    @Enumerated(EnumType.STRING)
    @Column(name = "domain", nullable = false, length = 30)
    private LevelTestDomain domain;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 60)
    private LevelTestItemType itemType;

    @Column(name = "complexity_band", nullable = false)
    private int complexityBand;

    @Column(name = "generated_for_question_number", nullable = false)
    private int generatedForQuestionNumber;

    @Lob
    @Column(name = "instruction", nullable = false, columnDefinition = "TEXT")
    private String instruction;

    @Column(name = "instruction_language", nullable = false, length = 20)
    private String instructionLanguage;

    @Enumerated(EnumType.STRING)
    @Column(name = "answer_mode", nullable = false, length = 20)
    private LevelTestAnswerMode answerMode;

    @Column(name = "answer_language", length = 20)
    private String answerLanguage;

    @Lob
    @Column(name = "prompt_text", nullable = false, columnDefinition = "TEXT")
    private String promptText;

    @Lob
    @Column(name = "options_json", nullable = false, columnDefinition = "TEXT")
    private String optionsJson;

    @Lob
    @Column(name = "internal_answer_key_json", nullable = false, columnDefinition = "TEXT")
    private String internalAnswerKeyJson;

    @Lob
    @Column(name = "reference_payload_json", nullable = false, columnDefinition = "TEXT")
    private String referencePayloadJson;

    @Lob
    @Column(name = "diversity_metadata_json", nullable = false, columnDefinition = "TEXT")
    private String diversityMetadataJson;

    @Column(name = "scenario_category", length = 50)
    private String scenarioCategory;

    @Column(name = "communicative_intent", length = 50)
    private String communicativeIntent;

    @Column(name = "task_archetype", length = 100)
    private String taskArchetype;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "similarity_key", nullable = false, length = 64)
    private String similarityKey;

    @Column(name = "max_answer_length")
    private Integer maxAnswerLength;

    @Column(name = "max_audio_seconds")
    private Integer maxAudioSeconds;

    @Column(name = "reference_audio_object_key", length = 1000)
    private String referenceAudioObjectKey;

    @Column(name = "reference_audio_content_type", length = 100)
    private String referenceAudioContentType;

    @Column(name = "reference_audio_duration_ms")
    private Integer referenceAudioDurationMs;

    @Column(name = "reference_audio_checksum_sha256", length = 64)
    private String referenceAudioChecksumSha256;

    @Column(name = "generation_version", length = 100)
    private String generationVersion;

    @Column(name = "prompt_version", length = 100)
    private String promptVersion;

    @Column(name = "policy_version", nullable = false, length = 100)
    private String policyVersion;

    @Column(name = "model_config_version", nullable = false, length = 100)
    private String modelConfigVersion;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "usage_count", nullable = false)
    private long usageCount;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "quarantine_reason", length = 100)
    private String quarantineReason;

    @Column(name = "quarantined_at")
    private LocalDateTime quarantinedAt;

    @Column(name = "replacement_pool_question_id")
    private Long replacementPoolQuestionId;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    private LevelTestQuestionPool(
            String originLanguage,
            String learningLanguage,
            int generatedForQuestionNumber,
            AiLevelTestQuestionResponseDto response,
            String optionsJson,
            String internalAnswerKeyJson,
            String referencePayloadJson,
            String diversityMetadataJson,
            String policyVersion,
            String modelConfigVersion,
            LocalDateTime generatedAt
    ) {
        this.originLanguage = originLanguage;
        this.learningLanguage = learningLanguage;
        this.domain = response.domain();
        this.itemType = response.itemType();
        this.complexityBand = response.complexityBand();
        this.generatedForQuestionNumber = generatedForQuestionNumber;
        this.instruction = response.instruction();
        this.instructionLanguage = response.instructionLanguage();
        this.answerMode = response.answerMode();
        this.answerLanguage = response.answerLanguage();
        this.promptText = response.promptText();
        this.optionsJson = optionsJson;
        this.internalAnswerKeyJson = internalAnswerKeyJson;
        this.referencePayloadJson = referencePayloadJson;
        this.diversityMetadataJson = diversityMetadataJson;
        this.scenarioCategory = response.diversityMetadata().scenarioCategory();
        this.communicativeIntent = response.diversityMetadata().communicativeIntent();
        this.taskArchetype = response.diversityMetadata().taskArchetype();
        this.contentHash = response.diversityMetadata().contentHash();
        this.similarityKey = response.diversityMetadata().similarityKey();
        this.maxAnswerLength = response.maxAnswerLength();
        this.maxAudioSeconds = response.maxAudioSeconds();
        if (response.referenceAudio() != null) {
            this.referenceAudioObjectKey = response.referenceAudio().objectKey();
            this.referenceAudioContentType = response.referenceAudio().contentType();
            this.referenceAudioDurationMs = response.referenceAudio().durationMs();
            this.referenceAudioChecksumSha256 = response.referenceAudio().checksumSha256();
        }
        this.generationVersion = response.generationVersion();
        this.promptVersion = response.promptVersion();
        this.policyVersion = policyVersion;
        this.modelConfigVersion = modelConfigVersion;
        this.generatedAt = generatedAt;
    }

    public static LevelTestQuestionPool create(
            String originLanguage,
            String learningLanguage,
            int generatedForQuestionNumber,
            AiLevelTestQuestionResponseDto response,
            LanguageLearningJsonCodec jsonCodec,
            String policyVersion,
            String modelConfigVersion,
            LocalDateTime generatedAt
    ) {
        return new LevelTestQuestionPool(
                originLanguage,
                learningLanguage,
                generatedForQuestionNumber,
                response,
                jsonCodec.write(response.options() == null ? List.of() : response.options()),
                jsonCodec.write(response.internalAnswerKey()),
                jsonCodec.write(response.referencePayload()),
                jsonCodec.write(response.diversityMetadata()),
                policyVersion,
                modelConfigVersion,
                generatedAt
        );
    }

    public void attachReferenceAudioIfMissing(
            String objectKey,
            String contentType,
            Integer durationMs,
            String checksumSha256
    ) {
        if (referenceAudioObjectKey != null) {
            return;
        }
        referenceAudioObjectKey = objectKey;
        referenceAudioContentType = contentType;
        referenceAudioDurationMs = durationMs;
        referenceAudioChecksumSha256 = checksumSha256;
    }

    public void markUsed(LocalDateTime now) {
        usageCount++;
        lastUsedAt = now;
    }

    public void quarantine(String reason, LocalDateTime now) {
        active = false;
        quarantineReason = reason == null || reason.isBlank()
                ? "UNKNOWN_INVALID_CONTENT"
                : reason.substring(0, Math.min(100, reason.length()));
        quarantinedAt = now;
        replacementPoolQuestionId = null;
    }

    public void markReplaced(Long replacementPoolQuestionId) {
        this.replacementPoolQuestionId = replacementPoolQuestionId;
    }
}
