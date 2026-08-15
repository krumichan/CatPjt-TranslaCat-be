package jp.co.translacat.domain.languagelearning.speaking.session.service;

import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningAdminSetting;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningUserSetting;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningAdminSettingQueryService;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningUserSettingQueryService;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingSessionStatus;
import jp.co.translacat.domain.languagelearning.speaking.session.dto.response.SpeakingDailyUsageResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.session.dto.response.SpeakingSessionResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.session.repository.SpeakingSessionRepository;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpeakingSessionQueryService {

    private final SpeakingSessionRepository sessionRepository;
    private final SpeakingSessionUsageQueryService usageQueryService;
    private final LanguageLearningAdminSettingQueryService adminSettingQueryService;
    private final LanguageLearningUserSettingQueryService userSettingQueryService;

    public SpeakingSession getOwnedEntity(Long userId, Long sessionId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(
                        "Speaking Session을 찾을 수 없습니다.",
                        LanguageLearningErrorCode.SESSION_NOT_FOUND
                ));
    }

    public SpeakingSession getOwnedEntityForUpdate(
            Long userId,
            Long sessionId
    ) {
        return sessionRepository.findOneByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(
                        "Speaking Session을 찾을 수 없습니다.",
                        LanguageLearningErrorCode.SESSION_NOT_FOUND
                ));
    }

    public Optional<SpeakingSession> findActiveEntity(Long userId) {
        return sessionRepository
                .findFirstByUserIdAndStatusOrderByStartedAtDesc(
                        userId,
                        SpeakingSessionStatus.IN_PROGRESS
                );
    }

    public SpeakingSessionResponseDto toResponse(
            Long userId,
            SpeakingSession session
    ) {
        boolean audioAvailable =
                session.getOpeningAssistantAudioObjectKey() != null;

        return new SpeakingSessionResponseDto(
                session.getId(),
                session.getLearningDate(),
                session.getTopic() == null ? null : session.getTopic().getId(),
                session.getTopicTitle(),
                session.getTopicCategory(),
                session.getTopicVersion(),
                session.getCustomTopic(),
                session.getGoal(),
                session.getPersona(),
                session.getOriginLanguage(),
                session.getLearningLanguage(),
                session.getStatus(),
                session.getEvaluationStatus(),
                session.getConversationStartMode(),
                session.getResolvedStartMode(),
                session.getCorrectionMode(),
                session.getTargetMinutes(),
                session.getMaxTurns(),
                session.getCompletedTurns(),
                session.getTotalDurationSeconds(),
                session.getVoiceId(),
                session.getPlaybackSpeed(),
                session.getOpeningAssistantText(),
                audioAvailable
                        ? "/api/v1/language-learning/speaking/sessions/"
                        + session.getId() + "/audio/opening"
                        : null,
                session.getSessionSummary(),
                session.getStartedAt(),
                session.getCompletedAt(),
                session.getLastActivityAt()
        );
    }

    public SpeakingDailyUsageResponseDto getDailyUsage(Long userId) {
        LanguageLearningUserSetting setting =
                userSettingQueryService.getOrCreateEntity(userId);
        LanguageLearningAdminSetting admin =
                adminSettingQueryService.getOrCreateEntity();
        return usageQueryService.getDailyUsage(
                userId,
                userSettingQueryService.resolveToday(setting),
                admin,
                setting
        );
    }
}
