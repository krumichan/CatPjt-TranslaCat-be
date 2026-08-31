package jp.co.translacat.domain.languagelearning.level.entity;

import jakarta.persistence.*;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestAnswerMode;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestDifficulty;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestDomain;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestItemStatus;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestItemType;
import jp.co.translacat.domain.languagelearning.level.pool.entity.LevelTestQuestionPool;
import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "language_learning_level_test_item",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ll_level_item_session_no",
                columnNames = {"session_id", "question_number"}
        ),
        indexes = @Index(
                name = "idx_ll_level_item_session",
                columnList = "session_id,question_number"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LevelTestItem extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private LevelTestSession session;

    @Column(name = "question_number", nullable = false)
    private int questionNumber;

    @Column(name = "pool_question_id")
    private Long poolQuestionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LevelTestDifficulty difficulty;

    @Lob
    @Column(name = "origin_text", nullable = false, columnDefinition = "TEXT")
    private String originText;

    @Lob
    @Column(name = "focus_metrics_json", nullable = false, columnDefinition = "TEXT")
    private String focusMetricsJson;

    @Lob
    @Column(name = "focus_reason", nullable = false, columnDefinition = "TEXT")
    private String focusReason;

    @Column(name = "prompt_version", length = 100)
    private String promptVersion;

    @Lob
    @Column(name = "answer_text", columnDefinition = "TEXT")
    private String answerText;

    private LocalDateTime answeredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "domain", length = 30)
    private LevelTestDomain domain;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", length = 60)
    private LevelTestItemType itemType;

    @Enumerated(EnumType.STRING)
    @Column(name = "answer_mode", length = 20)
    private LevelTestAnswerMode answerMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_status", length = 30)
    private LevelTestItemStatus status;

    @Column(name = "complexity_band")
    private Integer complexityBand;

    @Column(name = "instruction_language", length = 20)
    private String instructionLanguage;

    @Column(name = "answer_language", length = 20)
    private String answerLanguage;

    @Lob
    @Column(name = "instruction", columnDefinition = "TEXT")
    private String instruction;

    @Lob
    @Column(name = "prompt_text", columnDefinition = "TEXT")
    private String promptText;

    @Lob
    @Column(name = "options_json", columnDefinition = "TEXT")
    private String optionsJson;

    @Lob
    @Column(name = "internal_answer_key_json", columnDefinition = "TEXT")
    private String internalAnswerKeyJson;

    @Lob
    @Column(name = "reference_payload_json", columnDefinition = "TEXT")
    private String referencePayloadJson;

    @Lob
    @Column(name = "diversity_metadata_json", columnDefinition = "TEXT")
    private String diversityMetadataJson;

    @Column(name = "max_answer_length")
    private Integer maxAnswerLength;

    @Column(name = "max_audio_seconds")
    private Integer maxAudioSeconds;

    @Column(name = "generation_version", length = 100)
    private String generationVersion;

    @Column(name = "reference_audio_object_key", length = 1000)
    private String referenceAudioObjectKey;

    @Column(name = "reference_audio_content_type", length = 100)
    private String referenceAudioContentType;

    private LevelTestItem(
            LevelTestSession session,
            int questionNumber,
            LevelTestDomain domain,
            LevelTestItemType itemType,
            int complexityBand,
            String instruction,
            String instructionLanguage,
            LevelTestAnswerMode answerMode,
            String answerLanguage,
            String promptText,
            String optionsJson,
            String internalAnswerKeyJson,
            String referencePayloadJson,
            String diversityMetadataJson,
            Integer maxAnswerLength,
            Integer maxAudioSeconds,
            String generationVersion,
            String promptVersion
    ) {
        this.session = session;
        this.questionNumber = questionNumber;
        this.difficulty = toLegacyDifficulty(complexityBand);
        this.originText = promptText;
        this.focusMetricsJson = "[]";
        this.focusReason = instruction;
        this.promptVersion = promptVersion;
        this.domain = domain;
        this.itemType = itemType;
        this.answerMode = answerMode;
        this.status = LevelTestItemStatus.READY;
        this.complexityBand = complexityBand;
        this.instructionLanguage = instructionLanguage;
        this.answerLanguage = answerLanguage;
        this.instruction = instruction;
        this.promptText = promptText;
        this.optionsJson = optionsJson;
        this.internalAnswerKeyJson = internalAnswerKeyJson;
        this.referencePayloadJson = referencePayloadJson;
        this.diversityMetadataJson = diversityMetadataJson;
        this.maxAnswerLength = maxAnswerLength;
        this.maxAudioSeconds = maxAudioSeconds;
        this.generationVersion = generationVersion;
    }

    public static LevelTestItem create(
            LevelTestSession session,
            int questionNumber,
            LevelTestDomain domain,
            LevelTestItemType itemType,
            int complexityBand,
            String instruction,
            String instructionLanguage,
            LevelTestAnswerMode answerMode,
            String answerLanguage,
            String promptText,
            String optionsJson,
            String internalAnswerKeyJson,
            String referencePayloadJson,
            String diversityMetadataJson,
            Integer maxAnswerLength,
            Integer maxAudioSeconds,
            String generationVersion,
            String promptVersion
    ) {
        return new LevelTestItem(
                session,
                questionNumber,
                domain,
                itemType,
                complexityBand,
                instruction,
                instructionLanguage,
                answerMode,
                answerLanguage,
                promptText,
                optionsJson,
                internalAnswerKeyJson,
                referencePayloadJson,
                diversityMetadataJson,
                maxAnswerLength,
                maxAudioSeconds,
                generationVersion,
                promptVersion
        );
    }


    public static LevelTestItem createFromPool(
            LevelTestSession session,
            int questionNumber,
            LevelTestQuestionPool poolQuestion
    ) {
        LevelTestItem item = new LevelTestItem(
                session,
                questionNumber,
                poolQuestion.getDomain(),
                poolQuestion.getItemType(),
                poolQuestion.getComplexityBand(),
                poolQuestion.getInstruction(),
                poolQuestion.getInstructionLanguage(),
                poolQuestion.getAnswerMode(),
                poolQuestion.getAnswerLanguage(),
                poolQuestion.getPromptText(),
                poolQuestion.getOptionsJson(),
                poolQuestion.getInternalAnswerKeyJson(),
                poolQuestion.getReferencePayloadJson(),
                poolQuestion.getDiversityMetadataJson(),
                poolQuestion.getMaxAnswerLength(),
                poolQuestion.getMaxAudioSeconds(),
                poolQuestion.getGenerationVersion(),
                poolQuestion.getPromptVersion()
        );
        item.poolQuestionId = poolQuestion.getId();
        if (poolQuestion.getReferenceAudioObjectKey() != null) {
            item.attachReferenceAudio(
                    poolQuestion.getReferenceAudioObjectKey(),
                    poolQuestion.getReferenceAudioContentType()
            );
        }
        return item;
    }

    public void markAnswered() {
        status = LevelTestItemStatus.ANSWERED;
        answeredAt = LocalDateTime.now();
    }

    public void markEvaluating() {
        status = LevelTestItemStatus.EVALUATING;
    }

    public void markEvaluated() {
        status = LevelTestItemStatus.EVALUATED;
    }

    public void markEvaluationFailed() {
        status = LevelTestItemStatus.EVALUATION_FAILED;
    }

    public void attachReferenceAudio(
            String objectKey,
            String contentType
    ) {
        referenceAudioObjectKey = objectKey;
        referenceAudioContentType = contentType;
    }

    public int getComplexityBandValue() {
        return complexityBand == null ? 2 : complexityBand;
    }

    private static LevelTestDifficulty toLegacyDifficulty(int complexityBand) {
        if (complexityBand <= 2) {
            return LevelTestDifficulty.EASY;
        }
        if (complexityBand >= 4) {
            return LevelTestDifficulty.CHALLENGE;
        }
        return LevelTestDifficulty.NORMAL;
    }
}
