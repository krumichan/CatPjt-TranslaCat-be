package jp.co.translacat.domain.languagelearning.listening.report.service;

import jp.co.translacat.domain.languagelearning.listening.dto.ListeningApiContract;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskStatus;
import jp.co.translacat.domain.languagelearning.listening.report.entity.ListeningEvaluationReport;
import jp.co.translacat.domain.languagelearning.listening.report.repository.ListeningEvaluationReportRepository;
import jp.co.translacat.domain.languagelearning.listening.response.entity.ListeningTaskResponse;
import jp.co.translacat.domain.languagelearning.listening.response.repository.ListeningTaskResponseRepository;
import jp.co.translacat.domain.languagelearning.listening.setting.service.ListeningPolicySettingQueryService;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ListeningEvaluationReportCommandService {

    private final ListeningEvaluationReportRepository reportRepository;
    private final ListeningTaskResponseRepository responseRepository;
    private final ListeningPolicySettingQueryService policySettingService;

    @Transactional
    public ListeningApiContract.EvaluationReportView report(
            Long userId,
            Long taskResponseId,
            ListeningApiContract.EvaluationReportRequest request
    ) {
        if (request == null
                || request.reasonCode() == null
                || request.reasonCode().isBlank()
                || request.reasonCode().length() > 100
                || (request.comment() != null
                && request.comment().length() > 2000)
                || request.idempotencyKey() == null
                || request.idempotencyKey().isBlank()
                || request.idempotencyKey().length() > 200) {
            throw new BusinessException(
                    "Listening 평가 신고 정보가 필요합니다.",
                    LanguageLearningErrorCode.LISTENING_INVALID_STATE
            );
        }

        var existing = reportRepository
                .findByUserIdAndTaskResponseIdAndIdempotencyKey(
                        userId,
                        taskResponseId,
                        request.idempotencyKey()
                );

        if (existing.isPresent()) {
            return view(existing.get());
        }

        if (reportRepository.findFirstByUserIdAndTaskResponseId(
                userId,
                taskResponseId
        ).isPresent()) {
            throw new BusinessException(
                    "이미 신고한 Listening 평가입니다.",
                    LanguageLearningErrorCode.LISTENING_REPORT_ALREADY_SUBMITTED
            );
        }

        ListeningTaskResponse response = responseRepository
                .findByIdAndAttemptSessionUserId(taskResponseId, userId)
                .orElseThrow(() -> new BusinessException(
                        "Listening 평가 응답을 찾을 수 없습니다.",
                        LanguageLearningErrorCode.DAILY_ITEM_NOT_FOUND
                ));

        if (response.getAttempt().isAnswerRevealed()
                || (response.getStatus() != ListeningTaskStatus.EVALUATED
                && response.getStatus() != ListeningTaskStatus.NOT_EVALUABLE)) {
            throw new BusinessException(
                    "신고 가능한 Listening 평가 결과가 없습니다.",
                    LanguageLearningErrorCode.LISTENING_INVALID_STATE
            );
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime retention = null;

        if (request.consentToRetainAudio()) {
            if (response.getUserAudioObjectKey() == null
                    || response.getAudioDeletedAt() != null
                    || (response.getAudioRetentionUntil() != null
                    && response.getAudioRetentionUntil()
                            .isBefore(now))) {
                throw new BusinessException(
                        "신고 Audio의 보관 기간이 만료되었습니다.",
                        LanguageLearningErrorCode.LISTENING_REPORT_AUDIO_EXPIRED
                );
            }

            int reportedDays = policySettingService.get()
                    .getReportedAudioRetentionDays();
            LocalDateTime requestedRetention = now.plusDays(reportedDays);
            LocalDateTime absoluteRetention = response.getCreatedAt()
                    .plusDays(reportedDays);
            retention = requestedRetention.isAfter(absoluteRetention)
                    ? absoluteRetention
                    : requestedRetention;
            response.extendAudioRetention(retention);
        }

        ListeningEvaluationReport report = reportRepository.save(
                ListeningEvaluationReport.create(
                        response.getAttempt().getSession().getUser(),
                        response,
                        request.reasonCode(),
                        request.comment(),
                        request.consentToRetainAudio(),
                        retention,
                        request.idempotencyKey()
                )
        );

        return view(report);
    }

    private ListeningApiContract.EvaluationReportView view(
            ListeningEvaluationReport value
    ) {
        return new ListeningApiContract.EvaluationReportView(
                value.getId(),
                value.getTaskResponse().getId(),
                value.getStatus().name(),
                value.isConsentToRetainAudio(),
                value.getAudioRetentionUntil()
        );
    }
}
