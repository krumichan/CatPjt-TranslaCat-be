package jp.co.translacat.domain.languagelearning.speaking.report.service;

import jp.co.translacat.domain.languagelearning.speaking.report.dto.response.SttErrorReportResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.report.entity.SttErrorReport;
import jp.co.translacat.domain.languagelearning.speaking.report.repository.SttErrorReportRepository;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SttErrorReportQueryService {

    private final SttErrorReportRepository reportRepository;

    public SttErrorReportResponseDto get(Long userId, Long reportId) {
        return toResponse(
                reportRepository.findByIdAndUserId(reportId, userId)
                        .orElseThrow(() -> new BusinessException(
                                "STT Error Report를 찾을 수 없습니다.",
                                LanguageLearningErrorCode.STT_REPORT_NOT_FOUND
                        ))
        );
    }

    public SttErrorReportResponseDto toResponse(SttErrorReport report) {
        return new SttErrorReportResponseDto(
                report.getId(),
                report.getReportReference(),
                report.getSession().getId(),
                report.getTurn().getId(),
                report.getReportType(),
                report.getReportStatus(),
                report.getExpectedText(),
                report.isAudioAnalysisConsent(),
                report.getAudioRetentionUntil(),
                report.isSupportRequested(),
                report.getSupportReference(),
                report.getResolvedAt()
        );
    }
}
