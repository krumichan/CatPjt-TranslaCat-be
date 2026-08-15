package jp.co.translacat.domain.languagelearning.speaking.session.service;

import jp.co.translacat.domain.languagelearning.speaking.session.dto.response.SpeakingDailyUsageResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.session.repository.SpeakingSessionRepository;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningAdminSetting;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningUserSetting;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpeakingSessionUsageQueryService {

    private final SpeakingSessionRepository sessionRepository;

    public SpeakingDailyUsageResponseDto getDailyUsage(
            Long userId,
            LocalDate learningDate,
            LanguageLearningAdminSetting admin,
            LanguageLearningUserSetting userSetting
    ) {
        List<SpeakingSession> sessions = sessionRepository
                .findAllByUserIdAndLearningDate(userId, learningDate);

        double minutes = sessions.stream()
                .mapToLong(SpeakingSession::getTotalDurationSeconds)
                .sum() / 60.0;

        return new SpeakingDailyUsageResponseDto(
                sessions.size(),
                round(minutes),
                admin.getDailySpeakingSessionLimit(),
                admin.getDailySpeakingHardLimitMinutes(),
                userSetting.getDailySpeakingGoalMinutes()
        );
    }

    public void requireRemainingLimit(
            Long userId,
            LocalDate learningDate,
            LanguageLearningAdminSetting admin,
            LanguageLearningUserSetting userSetting
    ) {
        SpeakingDailyUsageResponseDto usage = getDailyUsage(
                userId,
                learningDate,
                admin,
                userSetting
        );

        boolean sessionExceeded = usage.sessionCount()
                >= admin.getDailySpeakingSessionLimit();
        boolean minutesExceeded = usage.usedMinutes()
                >= admin.getDailySpeakingHardLimitMinutes();

        if (sessionExceeded || minutesExceeded) {
            throw new jp.co.translacat.global.exception.BusinessException(
                    "일일 Speaking 사용량을 초과했습니다.",
                    jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode
                            .DAILY_LIMIT_EXCEEDED
            );
        }
    }

    public void requireTurnAllowed(
            Long userId,
            LocalDate learningDate,
            long additionalDurationSeconds,
            jp.co.translacat.domain.languagelearning.speaking.session.model.SpeakingSessionPolicySnapshot snapshot
    ) {
        long usedSeconds = sessionRepository
                .findAllByUserIdAndLearningDate(userId, learningDate)
                .stream()
                .mapToLong(SpeakingSession::getTotalDurationSeconds)
                .sum();
        long projected = usedSeconds + Math.max(0, additionalDurationSeconds);
        if (projected > snapshot.dailySpeakingHardLimitSeconds()) {
            throw new jp.co.translacat.global.exception.BusinessException(
                    "일일 Speaking 시간 상한을 초과합니다.",
                    jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode
                            .DAILY_LIMIT_EXCEEDED
            );
        }
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
