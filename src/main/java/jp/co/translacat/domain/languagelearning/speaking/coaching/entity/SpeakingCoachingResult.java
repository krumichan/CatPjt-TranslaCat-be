package jp.co.translacat.domain.languagelearning.speaking.coaching.entity;

import jakarta.persistence.*;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.global.jpa.BaseAuditable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "language_learning_speaking_coaching_result",
        uniqueConstraints = @UniqueConstraint(name = "uk_ll_speaking_coaching_session", columnNames = "session_id"),
        indexes = @Index(name = "idx_ll_speaking_coaching_policy", columnList = "result_policy_version,id"))
public class SpeakingCoachingResult extends BaseAuditable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private SpeakingSession session;
    @Column(name = "result_policy_version", nullable = false, length = 100)
    private String resultPolicyVersion;
    @Column(name = "schema_version", nullable = false, length = 100)
    private String schemaVersion;
    @Column(name = "source_snapshot_hash", nullable = false, length = 128)
    private String sourceSnapshotHash;
    @Column(name = "content_status", nullable = false, length = 40)
    private String contentStatus;
    @Lob @Column(name = "limitation_reasons_json", nullable = false, columnDefinition = "TEXT")
    private String limitationReasonsJson;
    @Lob @Column(name = "items_json", nullable = false, columnDefinition = "LONGTEXT")
    private String itemsJson;
    @Column(name = "prompt_version", nullable = false, length = 100)
    private String promptVersion;
    @Lob @Column(name = "usage_json", nullable = false, columnDefinition = "TEXT")
    private String usageJson;

    public static SpeakingCoachingResult create(
            SpeakingSession session, String resultPolicyVersion, String schemaVersion,
            String sourceSnapshotHash, String contentStatus, String limitationReasonsJson,
            String itemsJson, String promptVersion, String usageJson
    ) {
        SpeakingCoachingResult value = new SpeakingCoachingResult();
        value.session = Objects.requireNonNull(session);
        value.resultPolicyVersion = Objects.requireNonNull(resultPolicyVersion);
        value.schemaVersion = Objects.requireNonNull(schemaVersion);
        value.sourceSnapshotHash = Objects.requireNonNull(sourceSnapshotHash);
        value.contentStatus = Objects.requireNonNull(contentStatus);
        value.limitationReasonsJson = Objects.requireNonNull(limitationReasonsJson);
        value.itemsJson = Objects.requireNonNull(itemsJson);
        value.promptVersion = Objects.requireNonNull(promptVersion);
        value.usageJson = Objects.requireNonNull(usageJson);
        return value;
    }
}
