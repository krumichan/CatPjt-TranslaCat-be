package jp.co.translacat.domain.languagelearning.listening.daily.service;

import jp.co.translacat.domain.languagelearning.ai.dto.model.SelectedKeywordDto;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.keyword.facade.KeywordSelectionFacade;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningDifficulty;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningOutboxType;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.ListeningDailySet;
import jp.co.translacat.domain.languagelearning.listening.daily.model.ListeningGenerationCommand;
import jp.co.translacat.domain.languagelearning.listening.daily.repository.ListeningDailySetRepository;
import jp.co.translacat.domain.languagelearning.listening.dto.ListeningApiContract;
import jp.co.translacat.domain.languagelearning.listening.outbox.service.ListeningOutboxCommandService;
import jp.co.translacat.domain.languagelearning.listening.setting.entity.ListeningPolicySetting;
import jp.co.translacat.domain.languagelearning.listening.setting.service.ListeningPolicySettingQueryService;
import jp.co.translacat.domain.languagelearning.profile.service.LearningProfileAiContextService;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningAdminSetting;
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
import java.util.List;

@Service
@RequiredArgsConstructor
public class ListeningDailySetCommandService {

    private final ListeningDailySetRepository dailySetRepository;
    private final ListeningPolicySettingQueryService policySettingService;
    private final LanguageLearningUserSettingQueryService userSettingService;
    private final LanguageLearningAdminSettingQueryService adminSettingService;
    private final KeywordSelectionFacade keywordSelectionFacade;
    private final LearningProfileAiContextService profileContextService;
    private final ListeningOutboxCommandService outboxCommandService;
    private final LanguageLearningJsonCodec jsonCodec;
    private final UserRepository userRepository;

    @Transactional
    public ListeningDailySet getOrCreate(
            Long userId,
            ListeningApiContract.DailySetCreateRequest request
    ) {
        LanguageLearningUserSetting userSetting =
                userSettingService.getOrCreateEntity(userId);
        userSettingService.requireConfigured(userSetting);
        LocalDate today = userSettingService.resolveToday(userSetting);
        var existing = dailySetRepository
                .findByUserIdAndLearningDateAndLearningLanguage(
                        userId,
                        today,
                        userSetting.getLearningLanguage()
                );

        if (existing.isPresent()) {
            return existing.get();
        }

        ListeningPolicySetting policy = policySettingService.get();

        if (!policy.isEnabled()) {
            throw new BusinessException(
                    "Listening 학습 기능이 비활성화되어 있습니다.",
                    LanguageLearningErrorCode.LISTENING_SETTING_REQUIRED
            );
        }

        int itemCount;

        try {
            Integer requestedItemCount = request == null
                    ? null
                    : request.itemCount();
            itemCount = policy.resolveItemCount(
                    requestedItemCount == null
                            ? userSetting.getDailyListeningGoalCount()
                            : requestedItemCount
            );
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    exception.getMessage(),
                    LanguageLearningErrorCode.LISTENING_INVALID_STATE
            );
        }

        ListeningDifficulty difficulty = request == null
                || request.difficulty() == null
                ? ListeningDifficulty.MY_LEVEL
                : request.difficulty();
        LanguageLearningAdminSetting admin =
                adminSettingService.getOrCreateEntity();
        List<SelectedKeywordDto> keywords =
                keywordSelectionFacade.selectForDailySet(userId, today, admin);
        User user = userRepository.getReferenceById(userId);
        ListeningDailySet dailySet = ListeningDailySet.create(
                user,
                today,
                userSetting.getOriginLanguage(),
                userSetting.getLearningLanguage(),
                difficulty,
                jsonCodec.write(new TopicSnapshot("daily", "Daily Listening")),
                jsonCodec.write(keywords),
                jsonCodec.write(profileContextService.buildSummary(userId)),
                policy.getProfilePolicyVersion(),
                itemCount
        );

        try {
            dailySet = dailySetRepository.saveAndFlush(dailySet);
        } catch (DataIntegrityViolationException exception) {
            return dailySetRepository
                    .findByUserIdAndLearningDateAndLearningLanguage(
                            userId,
                            today,
                            userSetting.getLearningLanguage()
                    ).orElseThrow(() -> exception);
        }

        outboxCommandService.enqueue(
                ListeningOutboxType.GENERATE_SET,
                dailySet.getId(),
                ListeningGenerationCommand.initial(),
                "listening:set:" + dailySet.getId() + ":generate:0"
        );

        return dailySet;
    }

    private record TopicSnapshot(String id, String title) {
    }
}
