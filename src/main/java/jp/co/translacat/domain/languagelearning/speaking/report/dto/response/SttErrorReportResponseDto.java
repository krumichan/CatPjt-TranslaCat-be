package jp.co.translacat.domain.languagelearning.speaking.report.dto.response;

import jp.co.translacat.domain.languagelearning.speaking.common.enums.SttReportStatus;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SttReportType;

import java.time.LocalDateTime;

public record SttErrorReportResponseDto(
        Long id,
        String reportReference,
        Long sessionId,
        Long turnId,
        SttReportType reportType,
        SttReportStatus reportStatus,
        String expectedText,
        boolean audioAnalysisConsent,
        LocalDateTime audioRetentionUntil,
        boolean supportRequested,
        String supportReference,
        LocalDateTime resolvedAt
) {
}
