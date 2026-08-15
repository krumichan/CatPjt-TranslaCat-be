package jp.co.translacat.domain.languagelearning.speaking.report.dto.request;

import jp.co.translacat.domain.languagelearning.speaking.common.enums.SttReportType;

import java.util.Map;

public record SttErrorReportCreateRequestDto(
        SttReportType reportType,
        String expectedText,
        boolean audioAnalysisConsent,
        Map<String, Object> clientAudioMetadata,
        boolean supportRequested
) {
}
