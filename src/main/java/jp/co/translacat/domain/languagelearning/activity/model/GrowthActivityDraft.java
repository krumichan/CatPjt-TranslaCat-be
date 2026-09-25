package jp.co.translacat.domain.languagelearning.activity.model;

import jp.co.translacat.domain.languagelearning.common.enums.*;
import jp.co.translacat.domain.languagelearning.growth.model.*;
import java.time.*;
import java.util.*;

/** Core transaction-local 명령 조립용이다. DB entity도, LL에 저장되었다는 영수증도 아니다. */
public final class GrowthActivityDraft {
    private final long userId;
    private final LearningSource source;
    private final String referenceId;
    private final LocalDate learningDate;
    private final String title;
    private final LocalDateTime startedAt;
    private final LocalDateTime completedAt;
    private long durationSeconds;
    private String status = "COMPLETED";
    private Double overallScore;
    private Double evaluationConfidence;
    private String metadataJson = "{}";
    public GrowthActivityDraft(long userId, LearningSource source, String referenceId, LocalDate date, String title,
                               long durationSeconds, LocalDateTime startedAt, LocalDateTime completedAt) {
        this.userId = userId; this.source = source; this.referenceId = referenceId; this.learningDate = date; this.title = title;
        this.durationSeconds = Math.max(0, durationSeconds); this.startedAt = startedAt; this.completedAt = completedAt;
    }
    public void markEvaluating() { status = "EVALUATING"; }
    public void markEvaluationFailed() { status = "EVALUATION_FAILED"; }
    public void markEvaluated(double score, double confidence) { status = "EVALUATED"; overallScore = score; evaluationConfidence = confidence; }
    public void markInsufficientEvidence(Double confidence) { status = "INSUFFICIENT_EVIDENCE"; overallScore = null; evaluationConfidence = confidence; }
    public void updateMetadataJson(String json) { metadataJson = json == null ? "{}" : json; }
    public void updateDuration(long duration) { durationSeconds = Math.max(0, duration); }
    public long userId() { return userId; }
    public Map<String, Object> payload() {
        return GrowthFacts.fields("source", source.name(), "referenceId", referenceId, "learningDate", learningDate.toString(),
            "title", title, "durationSeconds", durationSeconds, "status", status, "overallScore", overallScore, "evaluationConfidence", evaluationConfidence,
            "startedAt", startedAt.toString(), "completedAt", completedAt == null ? null : completedAt.toString(), "metadataJson", metadataJson);
    }
}
