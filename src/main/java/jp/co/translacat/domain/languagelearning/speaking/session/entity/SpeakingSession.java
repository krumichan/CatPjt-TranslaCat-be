package jp.co.translacat.domain.languagelearning.speaking.session.entity;

import jakarta.persistence.*;

import jp.co.translacat.domain.languagelearning.speaking.common.enums.ConversationStartMode;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.CorrectionMode;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingEvaluationStatus;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingSessionStatus;
import jp.co.translacat.domain.languagelearning.speaking.topic.entity.SpeakingTopic;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "language_learning_speaking_session",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ll_speaking_session_user_idempotency",
                columnNames = {"user_id", "create_idempotency_key"}
        ),
        indexes = {
                @Index(
                        name = "idx_ll_speaking_session_user_status",
                        columnList = "user_id,status"
                ),
                @Index(
                        name = "idx_ll_speaking_session_user_date",
                        columnList = "user_id,learning_date"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpeakingSession extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", updatable = false)
    private SpeakingTopic topic;

    @Column(name = "create_idempotency_key", nullable = false, length = 200)
    private String createIdempotencyKey;

    @Column(name = "learning_date", nullable = false)
    private LocalDate learningDate;

    @Column(name = "topic_title", nullable = false, length = 500)
    private String topicTitle;

    @Column(name = "topic_category", length = 100)
    private String topicCategory;

    @Column(name = "topic_version")
    private Integer topicVersion;

    @Column(name = "custom_topic", length = 500)
    private String customTopic;

    @Column(length = 1000)
    private String goal;

    @Column(length = 1000)
    private String persona;

    @Lob
    @Column(name = "selected_keywords_json", nullable = false, columnDefinition = "TEXT")
    private String selectedKeywordsJson = "[]";

    @Column(name = "origin_language", nullable = false, length = 20)
    private String originLanguage;

    @Column(name = "learning_language", nullable = false, length = 20)
    private String learningLanguage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SpeakingSessionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "evaluation_status", nullable = false, length = 40)
    private SpeakingEvaluationStatus evaluationStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "conversation_start_mode", nullable = false, length = 40)
    private ConversationStartMode conversationStartMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolved_start_mode", nullable = false, length = 40)
    private ConversationStartMode resolvedStartMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "correction_mode", nullable = false, length = 40)
    private CorrectionMode correctionMode;

    @Column(nullable = false)
    private int targetMinutes;

    @Column(nullable = false)
    private int maxTurns;

    @Column(nullable = false)
    private int completedTurns;

    @Column(nullable = false)
    private long totalDurationSeconds;

    @Column(nullable = false, length = 100)
    private String voiceId;

    @Column(nullable = false, length = 20)
    private String playbackSpeed;

    @Lob
    @Column(name = "policy_snapshot", nullable = false, columnDefinition = "TEXT")
    private String policySnapshotJson;

    @Lob
    @Column(name = "profile_snapshot", columnDefinition = "TEXT")
    private String profileSnapshotJson;

    @Lob
    @Column(name = "session_summary", columnDefinition = "TEXT")
    private String sessionSummary;

    @Lob
    @Column(name = "usage_summary", nullable = false, columnDefinition = "TEXT")
    private String usageSummaryJson = "{}";

    @Column(name = "opening_assistant_text", length = 4000)
    private String openingAssistantText;

    @Column(name = "opening_assistant_audio_object_key", length = 500)
    private String openingAssistantAudioObjectKey;

    @Column(name = "opening_assistant_audio_retention_until")
    private LocalDateTime openingAssistantAudioRetentionUntil;

    @Column(name = "evaluation_version", length = 100)
    private String evaluationVersion;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    @Column(nullable = false)
    private LocalDateTime lastActivityAt;

    private SpeakingSession(
            User user,
            SpeakingTopic topic,
            String createIdempotencyKey,
            LocalDate learningDate,
            String topicTitle,
            String topicCategory,
            Integer topicVersion,
            String customTopic,
            String goal,
            String persona,
            String selectedKeywordsJson,
            String originLanguage,
            String learningLanguage,
            ConversationStartMode requestedStartMode,
            ConversationStartMode resolvedStartMode,
            CorrectionMode correctionMode,
            int targetMinutes,
            int maxTurns,
            String voiceId,
            String playbackSpeed,
            String policySnapshotJson,
            String profileSnapshotJson
    ) {
        this.user = user;
        this.topic = topic;
        this.createIdempotencyKey = createIdempotencyKey;
        this.learningDate = learningDate;
        this.topicTitle = topicTitle;
        this.topicCategory = topicCategory;
        this.topicVersion = topicVersion;
        this.customTopic = customTopic;
        this.goal = goal;
        this.persona = persona;
        this.selectedKeywordsJson = selectedKeywordsJson == null ? "[]" : selectedKeywordsJson;
        this.originLanguage = originLanguage;
        this.learningLanguage = learningLanguage;
        this.status = SpeakingSessionStatus.IN_PROGRESS;
        this.evaluationStatus = SpeakingEvaluationStatus.NOT_REQUESTED;
        this.conversationStartMode = requestedStartMode;
        this.resolvedStartMode = resolvedStartMode;
        this.correctionMode = correctionMode;
        this.targetMinutes = targetMinutes;
        this.maxTurns = maxTurns;
        this.voiceId = voiceId;
        this.playbackSpeed = playbackSpeed;
        this.policySnapshotJson = policySnapshotJson;
        this.profileSnapshotJson = profileSnapshotJson;
        this.startedAt = LocalDateTime.now();
        this.lastActivityAt = this.startedAt;
    }

    public static SpeakingSession create(
            User user,
            SpeakingTopic topic,
            String createIdempotencyKey,
            LocalDate learningDate,
            String topicTitle,
            String topicCategory,
            Integer topicVersion,
            String customTopic,
            String goal,
            String persona,
            String selectedKeywordsJson,
            String originLanguage,
            String learningLanguage,
            ConversationStartMode requestedStartMode,
            ConversationStartMode resolvedStartMode,
            CorrectionMode correctionMode,
            int targetMinutes,
            int maxTurns,
            String voiceId,
            String playbackSpeed,
            String policySnapshotJson,
            String profileSnapshotJson
    ) {
        return new SpeakingSession(
                user,
                topic,
                createIdempotencyKey,
                learningDate,
                topicTitle,
                topicCategory,
                topicVersion,
                customTopic,
                goal,
                persona,
                selectedKeywordsJson,
                originLanguage,
                learningLanguage,
                requestedStartMode,
                resolvedStartMode,
                correctionMode,
                targetMinutes,
                maxTurns,
                voiceId,
                playbackSpeed,
                policySnapshotJson,
                profileSnapshotJson
        );
    }

    public boolean isActive() {
        return status == SpeakingSessionStatus.IN_PROGRESS;
    }

    public void registerCompletedTurn(
            double durationSeconds,
            String sessionSummary,
            String usageSummaryJson
    ) {
        this.completedTurns++;
        this.totalDurationSeconds += Math.max(
                0L,
                Math.round(durationSeconds)
        );
        if (sessionSummary != null) {
            this.sessionSummary = sessionSummary;
        }
        if (usageSummaryJson != null) {
            this.usageSummaryJson = usageSummaryJson;
        }
        this.lastActivityAt = LocalDateTime.now();
    }

    public void storeOpeningAssistant(
            String text,
            String audioObjectKey,
            LocalDateTime audioRetentionUntil,
            String sessionSummary,
            String usageSummaryJson
    ) {
        this.openingAssistantText = text;
        this.openingAssistantAudioObjectKey = audioObjectKey;
        this.openingAssistantAudioRetentionUntil = audioRetentionUntil;
        this.sessionSummary = sessionSummary;
        this.usageSummaryJson = usageSummaryJson == null
                ? "{}"
                : usageSummaryJson;
        this.lastActivityAt = LocalDateTime.now();
    }

    public void clearOpeningAssistantAudio() {
        this.openingAssistantAudioObjectKey = null;
        this.openingAssistantAudioRetentionUntil = null;
    }

    public void touch() {
        this.lastActivityAt = LocalDateTime.now();
    }

    public void expire() {
        if (isActive()) {
            this.status = SpeakingSessionStatus.EXPIRED;
            this.completedAt = LocalDateTime.now();
            this.lastActivityAt = this.completedAt;
        }
    }

    public void complete(boolean evaluationRequested) {
        if (!isActive()) {
            return;
        }

        this.status = SpeakingSessionStatus.COMPLETED;
        this.evaluationStatus = evaluationRequested
                ? SpeakingEvaluationStatus.PENDING
                : SpeakingEvaluationStatus.NOT_REQUESTED;
        this.completedAt = LocalDateTime.now();
        this.lastActivityAt = this.completedAt;
    }

    public void complete() {
        complete(true);
    }

    public void markEvaluating() {
        this.status = SpeakingSessionStatus.EVALUATING;
        this.evaluationStatus = SpeakingEvaluationStatus.EVALUATING;
    }

    public void markEvaluated(String evaluationVersion) {
        this.status = SpeakingSessionStatus.EVALUATED;
        this.evaluationStatus = SpeakingEvaluationStatus.EVALUATED;
        this.evaluationVersion = evaluationVersion;
    }

    public void markInsufficientEvidence(String evaluationVersion) {
        this.status = SpeakingSessionStatus.EVALUATED;
        this.evaluationStatus = SpeakingEvaluationStatus.INSUFFICIENT_EVIDENCE;
        this.evaluationVersion = evaluationVersion;
    }

    public void markEvaluationFailed() {
        this.status = SpeakingSessionStatus.EVALUATION_FAILED;
        this.evaluationStatus = SpeakingEvaluationStatus.FAILED;
    }
}
