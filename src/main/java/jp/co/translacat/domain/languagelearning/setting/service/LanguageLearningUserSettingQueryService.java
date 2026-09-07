package jp.co.translacat.domain.languagelearning.setting.service;

import com.fasterxml.jackson.core.type.TypeReference;

import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.domain.languagelearning.listening.setting.entity.ListeningPolicySetting;
import jp.co.translacat.domain.languagelearning.listening.setting.service.ListeningPolicySettingQueryService;
import jp.co.translacat.domain.languagelearning.setting.dto.response.UserSettingResponseDto;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningAdminSetting;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningUserSetting;
import jp.co.translacat.domain.languagelearning.setting.repository.LanguageLearningUserSettingRepository;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class LanguageLearningUserSettingQueryService {

    private static final String DEFAULT_TIMEZONE = "Asia/Tokyo";

    private final LanguageLearningUserSettingRepository repository;
    private final LanguageLearningAdminSettingQueryService adminSettingQueryService;
    private final ListeningPolicySettingQueryService listeningPolicySettingQueryService;
    private final LanguageLearningJsonCodec jsonCodec;
    private final UserRepository userRepository;

    @Transactional
    public LanguageLearningUserSetting getOrCreateEntity(Long userId) {
        LanguageLearningAdminSetting admin =
                adminSettingQueryService.getOrCreateEntity();

        LanguageLearningUserSetting setting = repository
                .findByUserId(userId)
                .orElseGet(() -> createSetting(userId, admin));

        LocalDate today = resolveToday(setting);
        setting.promoteIfEffective(today);
        setting.clampActiveAndPending(
                admin.getMinDailySentenceCount(),
                admin.getMaxDailySentenceCount()
        );
        setting.clampSpeakingGoalActiveAndPending(
                admin.getMinDailySpeakingGoalMinutes(),
                admin.getMaxDailySpeakingGoalMinutes()
        );
        ListeningPolicySetting listeningPolicy = listeningPolicySettingQueryService.get();
        setting.clampListeningGoalActiveAndPending(
                listeningPolicy.getMinItemCount(),
                listeningPolicy.getMaxItemCount()
        );

        return setting;
    }

    @Transactional
    public UserSettingResponseDto get(Long userId) {
        LanguageLearningAdminSetting admin =
                adminSettingQueryService.getOrCreateEntity();
        LanguageLearningUserSetting setting = getOrCreateEntity(userId);

        return toResponse(setting, admin);
    }

    public LocalDate resolveToday(LanguageLearningUserSetting setting) {
        return LocalDate.now(resolveZoneId(setting.getTimezone()));
    }

    @Transactional(readOnly = true)
    public LocalDate resolveToday(Long userId) {
        return repository.findByUserId(userId)
                .map(this::resolveToday)
                .orElseGet(() -> LocalDate.now(ZoneId.of(DEFAULT_TIMEZONE)));
    }

    public void requireConfigured(LanguageLearningUserSetting setting) {
        if (setting.getOriginLanguage() == null
                || setting.getLearningLanguage() == null) {
            throw new BusinessException(
                    "Origin Language와 Learning Language 설정이 필요합니다.",
                    LanguageLearningErrorCode.SETTING_NOT_CONFIGURED
            );
        }
    }

    public UserSettingResponseDto toResponse(
            LanguageLearningUserSetting setting,
            LanguageLearningAdminSetting admin
    ) {
        ListeningPolicySetting listeningPolicy = listeningPolicySettingQueryService.get();
        return new UserSettingResponseDto(
                setting.getOriginLanguage(),
                setting.getLearningLanguage(),
                setting.getTimezone(),
                setting.getDailySentenceCount(),
                setting.getDailySpeakingGoalMinutes(),
                setting.getSpeakingVoiceId(),
                setting.getSpeakingPlaybackSpeed(),
                setting.getDailyListeningGoalCount(),
                jsonCodec.read(
                        setting.getDefaultListeningTaskTypesJson(),
                        new TypeReference<java.util.List<ListeningTaskType>>() {
                        }
                ),
                setting.getPendingOriginLanguage(),
                setting.getPendingLearningLanguage(),
                setting.getPendingTimezone(),
                setting.getPendingDailySentenceCount(),
                setting.getPendingDailySpeakingGoalMinutes(),
                setting.getPendingDailyListeningGoalCount(),
                setting.getPendingEffectiveDate(),
                admin.getMinDailySentenceCount(),
                admin.getMaxDailySentenceCount(),
                admin.getMinDailySpeakingGoalMinutes(),
                admin.getMaxDailySpeakingGoalMinutes(),
                listeningPolicy.getMinItemCount(),
                listeningPolicy.getMaxItemCount(),
                setting.getOriginLanguage() != null
                        && setting.getLearningLanguage() != null
        );
    }

    private LanguageLearningUserSetting createSetting(
            Long userId,
            LanguageLearningAdminSetting admin
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        "사용자를 찾을 수 없습니다.",
                        LanguageLearningErrorCode.USER_NOT_FOUND
                ));

        LanguageLearningUserSetting setting =
                LanguageLearningUserSetting.create(
                        user,
                        admin.getDefaultDailySentenceCount()
                );
        setting.initialize(
                null,
                null,
                null,
                null,
                admin.getDefaultDailySpeakingGoalMinutes(),
                null,
                null
        );
        ListeningPolicySetting listeningPolicy = listeningPolicySettingQueryService.get();
        setting.initializeListening(
                listeningPolicy.getDefaultItemCount(),
                "[\"DICTATION\"]"
        );

        return repository.save(setting);
    }

    private ZoneId resolveZoneId(String timezone) {
        try {
            return ZoneId.of(
                    timezone == null ? DEFAULT_TIMEZONE : timezone
            );
        } catch (Exception e) {
            return ZoneId.of(DEFAULT_TIMEZONE);
        }
    }
}
