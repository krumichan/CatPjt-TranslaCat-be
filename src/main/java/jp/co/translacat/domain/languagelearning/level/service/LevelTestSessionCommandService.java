package jp.co.translacat.domain.languagelearning.level.service;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionStatus;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionType;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestSession;
import jp.co.translacat.domain.languagelearning.level.policy.LevelTestAdaptivePolicy;
import jp.co.translacat.domain.languagelearning.level.repository.LevelTestSessionRepository;
import jp.co.translacat.domain.languagelearning.profile.service.LearningProfileQueryService;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningUserSetting;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningAdminSettingQueryService;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningUserSettingQueryService;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Transactional
public class LevelTestSessionCommandService {

    private final LevelTestSessionRepository sessionRepository;
    private final LevelTestQueryService levelTestQueryService;
    private final LearningProfileQueryService profileQueryService;
    private final LanguageLearningUserSettingQueryService userSettingQueryService;
    private final LanguageLearningAdminSettingQueryService adminSettingQueryService;
    private final LevelTestAdaptivePolicy adaptivePolicy;
    private final UserRepository userRepository;

    public LevelTestSession start(
            Long userId,
            LevelTestSessionType requestedType,
            String idempotencyKey
    ) {
        validateAiEvaluationEnabled();

        String key = normalizeKey(idempotencyKey);
        LevelTestSessionType sessionType = requestedType == null
                ? LevelTestSessionType.INITIAL
                : requestedType;

        LevelTestSession idempotent = sessionRepository
                .findByUserIdAndIdempotencyKey(userId, key)
                .orElse(null);
        if (idempotent != null) {
            return idempotent;
        }

        LevelTestSession active = levelTestQueryService
                .getActiveSession(userId)
                .orElse(null);
        if (active != null) {
            return active;
        }

        LanguageLearningUserSetting setting =
                userSettingQueryService.getOrCreateEntity(userId);
        userSettingQueryService.requireConfigured(setting);

        validateSessionType(userId, sessionType);
        validateDailyLimit(userId, setting.getTimezone());

        Double baseLevelScore = profileQueryService
                .getOrCreate(userId)
                .getBaseLevelScore();
        int initialBand = adaptivePolicy.initialBand(
                baseLevelScore,
                sessionType == LevelTestSessionType.RECHECK
        );
        LocalDateTime now = LocalDateTime.now();

        try {
            return sessionRepository.save(LevelTestSession.start(
                    getUser(userId),
                    sessionType,
                    setting.getOriginLanguage(),
                    setting.getLearningLanguage(),
                    initialBand,
                    key,
                    now
            ));
        } catch (DataIntegrityViolationException exception) {
            return sessionRepository
                    .findByUserIdAndIdempotencyKey(userId, key)
                    .orElseThrow(() -> exception);
        }
    }

    private void validateSessionType(
            Long userId,
            LevelTestSessionType sessionType
    ) {
        boolean completed = levelTestQueryService
                .hasCompletedInitialTest(userId);

        if (sessionType == LevelTestSessionType.INITIAL && completed) {
            throw new BusinessException(
                    "최초 Level Test가 이미 완료되었습니다.",
                    LanguageLearningErrorCode.LEVEL_TEST_INVALID_STATE
            );
        }
        if (sessionType == LevelTestSessionType.RECHECK && !completed) {
            throw new BusinessException(
                    "최초 Level Test 완료 후 재측정할 수 있습니다.",
                    LanguageLearningErrorCode.LEVEL_TEST_INVALID_STATE
            );
        }
    }

    private void validateDailyLimit(Long userId, String timezone) {
        ZoneId zoneId = resolveZone(timezone);
        LocalDate today = LocalDate.now(zoneId);
        LocalDateTime from = today.atStartOfDay();
        LocalDateTime to = today.plusDays(1).atStartOfDay();

        if (sessionRepository
                .existsByUserIdAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
                        userId,
                        LevelTestSessionStatus.COMPLETED,
                        from,
                        to
                )) {
            throw new BusinessException(
                    "오늘의 공식 Level Test가 이미 완료되었습니다.",
                    LanguageLearningErrorCode.LEVEL_TEST_DAILY_LIMIT_REACHED
            );
        }
    }

    private void validateAiEvaluationEnabled() {
        if (!adminSettingQueryService
                .getOrCreateEntity()
                .isAiEvaluationEnabled()) {
            throw new BusinessException(
                    "AI 평가가 비활성화되어 있습니다.",
                    LanguageLearningErrorCode.SETTING_INVALID
            );
        }
    }

    private String normalizeKey(String value) {
        String key = value == null ? "" : value.trim();
        if (key.isBlank() || key.length() > 200) {
            throw new BusinessException(
                    "Level Test idempotencyKey가 필요합니다.",
                    LanguageLearningErrorCode.LEVEL_TEST_INVALID_STATE
            );
        }
        return key;
    }

    private ZoneId resolveZone(String timezone) {
        try {
            return ZoneId.of(
                    timezone == null ? "Asia/Tokyo" : timezone
            );
        } catch (Exception exception) {
            return ZoneId.of("Asia/Tokyo");
        }
    }

    private User getUser(Long userId) {
        return userRepository.findLockedById(userId)
                .orElseThrow(() -> new BusinessException(
                        "사용자를 찾을 수 없습니다.",
                        LanguageLearningErrorCode.USER_NOT_FOUND
                ));
    }
}
