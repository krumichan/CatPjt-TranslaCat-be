package jp.co.translacat.domain.languagelearning.speaking.report.entity;

import jakarta.persistence.*;

import jp.co.translacat.domain.languagelearning.speaking.common.enums.SttReportStatus;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SttReportType;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.turn.entity.SpeakingTurn;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "language_learning_stt_error_report",
        indexes = {
                @Index(
                        name = "idx_ll_stt_report_user",
                        columnList = "user_id,created_at"
                ),
                @Index(
                        name = "idx_ll_stt_report_turn",
                        columnList = "turn_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SttErrorReport extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String reportReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private SpeakingSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "turn_id", nullable = false, updatable = false)
    private SpeakingTurn turn;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 40)
    private SttReportType reportType;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_status", nullable = false, length = 40)
    private SttReportStatus reportStatus;

    @Column(name = "expected_text", length = 4000)
    private String expectedText;

    @Column(nullable = false)
    private boolean audioAnalysisConsent;

    private LocalDateTime audioRetentionUntil;

    @Lob
    @Column(name = "stt_provider_metadata", columnDefinition = "TEXT")
    private String sttProviderMetadataJson;

    @Lob
    @Column(name = "client_audio_metadata", columnDefinition = "TEXT")
    private String clientAudioMetadataJson;

    @Column(nullable = false)
    private boolean supportRequested;

    @Column(length = 100)
    private String supportReference;

    private LocalDateTime resolvedAt;

    private SttErrorReport(
            String reportReference,
            User user,
            SpeakingSession session,
            SpeakingTurn turn,
            SttReportType reportType,
            String expectedText,
            boolean audioAnalysisConsent,
            LocalDateTime audioRetentionUntil,
            String sttProviderMetadataJson,
            String clientAudioMetadataJson,
            boolean supportRequested,
            String supportReference
    ) {
        this.reportReference = reportReference;
        this.user = user;
        this.session = session;
        this.turn = turn;
        this.reportType = reportType;
        this.reportStatus = SttReportStatus.OPEN;
        this.expectedText = expectedText;
        this.audioAnalysisConsent = audioAnalysisConsent;
        this.audioRetentionUntil = audioRetentionUntil;
        this.sttProviderMetadataJson = sttProviderMetadataJson;
        this.clientAudioMetadataJson = clientAudioMetadataJson;
        this.supportRequested = supportRequested;
        this.supportReference = supportReference;
    }

    public static SttErrorReport create(
            String reportReference,
            User user,
            SpeakingSession session,
            SpeakingTurn turn,
            SttReportType reportType,
            String expectedText,
            boolean audioAnalysisConsent,
            LocalDateTime audioRetentionUntil,
            String sttProviderMetadataJson,
            String clientAudioMetadataJson,
            boolean supportRequested,
            String supportReference
    ) {
        return new SttErrorReport(
                reportReference,
                user,
                session,
                turn,
                reportType,
                expectedText,
                audioAnalysisConsent,
                audioRetentionUntil,
                sttProviderMetadataJson,
                clientAudioMetadataJson,
                supportRequested,
                supportReference
        );
    }

    public void requestSupport(String supportReference) {
        this.supportRequested = true;
        this.supportReference = supportReference;
    }

    public void resolve(SttReportStatus status) {
        this.reportStatus = status;
        this.resolvedAt = LocalDateTime.now();
    }
}
