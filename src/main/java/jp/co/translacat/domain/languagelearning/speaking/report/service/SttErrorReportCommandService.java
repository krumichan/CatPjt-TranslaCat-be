package jp.co.translacat.domain.languagelearning.speaking.report.service;

import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.speaking.report.dto.request.SttErrorReportCreateRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.report.entity.SttErrorReport;
import jp.co.translacat.domain.languagelearning.speaking.report.repository.SttErrorReportRepository;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.session.model.SpeakingSessionPolicySnapshot;
import jp.co.translacat.domain.languagelearning.speaking.session.service.SpeakingSessionPolicySnapshotService;
import jp.co.translacat.domain.languagelearning.speaking.session.service.SpeakingSessionQueryService;
import jp.co.translacat.domain.languagelearning.speaking.turn.entity.SpeakingTurn;
import jp.co.translacat.domain.languagelearning.speaking.turn.service.SpeakingTurnQueryService;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SttErrorReportCommandService {

    private final SttErrorReportRepository reportRepository;
    private final SpeakingSessionQueryService sessionQueryService;
    private final SpeakingTurnQueryService turnQueryService;
    private final SpeakingSessionPolicySnapshotService policySnapshotService;
    private final LanguageLearningJsonCodec jsonCodec;
    private final UserRepository userRepository;

    @Transactional
    public SttErrorReport create(
            Long userId,
            Long sessionId,
            Long turnId,
            SttErrorReportCreateRequestDto request
    ) {
        if (request == null || request.reportType() == null) {
            throw new BusinessException(
                    "STT Report Type이 필요합니다.",
                    LanguageLearningErrorCode.STT_REPORT_NOT_FOUND
            );
        }
        SpeakingSession session = sessionQueryService.getOwnedEntity(
                userId,
                sessionId
        );
        SpeakingTurn turn = turnQueryService.getOwnedEntity(
                userId,
                sessionId,
                turnId
        );
        SpeakingSessionPolicySnapshot snapshot = policySnapshotService.read(session);
        LocalDateTime retentionUntil = request.audioAnalysisConsent()
                && turn.getUserAudioObjectKey() != null
                ? LocalDateTime.now()
                .plusDays(snapshot.reportedAudioRetentionDays())
                : null;
        if (retentionUntil != null) {
            turn.extendUserAudioRetention(retentionUntil);
        }

        String reference = "STT-" + UUID.randomUUID()
                .toString().replace("-", "").substring(0, 16)
                .toUpperCase();
        String supportReference = request.supportRequested()
                ? "SUP-" + reference.substring(4)
                : null;
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        "사용자를 찾을 수 없습니다.",
                        LanguageLearningErrorCode.USER_NOT_FOUND
                ));

        return reportRepository.save(
                SttErrorReport.create(
                        reference,
                        user,
                        session,
                        turn,
                        request.reportType(),
                        trim(request.expectedText(), 4000),
                        request.audioAnalysisConsent(),
                        retentionUntil,
                        turn.getSttMetadataJson(),
                        jsonCodec.write(request.clientAudioMetadata()),
                        request.supportRequested(),
                        supportReference
                )
        );
    }

    @Transactional
    public SttErrorReport requestSupport(Long userId, Long reportId) {
        SttErrorReport report = reportRepository.findByIdAndUserId(
                reportId,
                userId
        ).orElseThrow(() -> new BusinessException(
                "STT Error Report를 찾을 수 없습니다.",
                LanguageLearningErrorCode.STT_REPORT_NOT_FOUND
        ));
        if (!report.isSupportRequested()) {
            report.requestSupport(
                    "SUP-" + report.getReportReference().substring(4)
            );
        }
        return report;
    }

    private String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.length() <= max
                ? cleaned
                : cleaned.substring(0, max);
    }
}
