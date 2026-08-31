package jp.co.translacat.domain.languagelearning.quality.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import jp.co.translacat.domain.languagelearning.quality.common.LanguageLearningContentSource;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "language_learning_generation_fingerprint",
        indexes = {
                @Index(
                        name = "idx_ll_gen_fp_user_source_lang_date",
                        columnList = "user_id,source_type,learning_language,generated_at"
                ),
                @Index(
                        name = "idx_ll_gen_fp_user_lang_hash",
                        columnList = "user_id,learning_language,content_hash"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LanguageLearningGenerationFingerprint extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private LanguageLearningContentSource sourceType;

    @Column(name = "source_id", nullable = false, length = 100)
    private String sourceId;

    @Column(name = "learning_language", nullable = false, length = 20)
    private String learningLanguage;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "content_hash", nullable = false, length = 100)
    private String contentHash;

    @Column(name = "similarity_key", length = 500)
    private String similarityKey;

    @Column(name = "content_excerpt", length = 500)
    private String contentExcerpt;

    @Column(name = "scenario_category", length = 50)
    private String scenarioCategory;

    @Column(name = "communicative_intent", length = 50)
    private String communicativeIntent;

    @Column(name = "task_archetype", length = 100)
    private String taskArchetype;

    @Lob
    @Column(name = "grammar_focus_json", columnDefinition = "TEXT")
    private String grammarFocusJson;

    @Column(name = "semantic_summary", length = 1000)
    private String semanticSummary;

    @Column(name = "policy_version", nullable = false, length = 100)
    private String policyVersion;

    private LanguageLearningGenerationFingerprint(
            User user,
            LanguageLearningContentSource sourceType,
            String sourceId,
            String learningLanguage,
            LocalDateTime generatedAt,
            String contentHash,
            String similarityKey,
            String contentExcerpt,
            String scenarioCategory,
            String communicativeIntent,
            String taskArchetype,
            String grammarFocusJson,
            String semanticSummary,
            String policyVersion
    ) {
        this.user = user;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.learningLanguage = learningLanguage;
        this.generatedAt = generatedAt;
        this.contentHash = contentHash;
        this.similarityKey = similarityKey;
        this.contentExcerpt = contentExcerpt;
        this.scenarioCategory = scenarioCategory;
        this.communicativeIntent = communicativeIntent;
        this.taskArchetype = taskArchetype;
        this.grammarFocusJson = grammarFocusJson;
        this.semanticSummary = semanticSummary;
        this.policyVersion = policyVersion;
    }

    public static LanguageLearningGenerationFingerprint create(
            User user,
            LanguageLearningContentSource sourceType,
            String sourceId,
            String learningLanguage,
            LocalDateTime generatedAt,
            String contentHash,
            String similarityKey,
            String contentExcerpt,
            String scenarioCategory,
            String communicativeIntent,
            String taskArchetype,
            String grammarFocusJson,
            String semanticSummary,
            String policyVersion
    ) {
        return new LanguageLearningGenerationFingerprint(
                user,
                sourceType,
                sourceId,
                learningLanguage,
                generatedAt,
                contentHash,
                similarityKey,
                contentExcerpt,
                scenarioCategory,
                communicativeIntent,
                taskArchetype,
                grammarFocusJson,
                semanticSummary,
                policyVersion
        );
    }
}
