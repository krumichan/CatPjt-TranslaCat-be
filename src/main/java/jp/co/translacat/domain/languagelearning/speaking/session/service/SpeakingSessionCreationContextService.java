package jp.co.translacat.domain.languagelearning.speaking.session.service;

import jp.co.translacat.domain.languagelearning.ai.dto.model.LearningProfileSummaryDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.SelectedKeywordDto;
import jp.co.translacat.domain.languagelearning.keyword.facade.KeywordSelectionFacade;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningAdminSetting;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningUserSetting;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningAdminSettingQueryService;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningUserSettingQueryService;
import jp.co.translacat.domain.languagelearning.speaking.session.dto.request.SpeakingSessionCreateRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.session.model.SpeakingSessionCreationContext;
import jp.co.translacat.domain.languagelearning.speaking.session.model.SpeakingSessionPolicySnapshot;
import jp.co.translacat.domain.languagelearning.speaking.session.policy.SpeakingSessionPolicy;
import jp.co.translacat.domain.languagelearning.speaking.topic.entity.SpeakingTopic;
import jp.co.translacat.domain.languagelearning.speaking.topic.service.SpeakingTopicQueryService;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SpeakingSessionCreationContextService {

    private final SpeakingSessionQueryService sessionQueryService;
    private final SpeakingSessionLifecycleService lifecycleService;
    private final SpeakingSessionUsageQueryService usageQueryService;
    private final SpeakingSessionPolicy sessionPolicy;
    private final SpeakingTopicQueryService topicQueryService;
    private final LanguageLearningAdminSettingQueryService adminSettingQueryService;
    private final LanguageLearningUserSettingQueryService userSettingQueryService;
    private final SpeakingProfileContextService speakingProfileContextService;
    private final KeywordSelectionFacade keywordSelectionFacade;
    private final SpeakingSessionPolicySnapshotService snapshotService;
    private final UserRepository userRepository;

    public SpeakingSessionCreationContext prepare(
            Long userId,
            SpeakingSessionCreateRequestDto request
    ) {
        LanguageLearningAdminSetting admin =
                adminSettingQueryService.getOrCreateEntity();
        LanguageLearningUserSetting setting =
                userSettingQueryService.getOrCreateEntity(userId);
        userSettingQueryService.requireConfigured(setting);
        sessionPolicy.validateCreate(request, admin);

        LocalDate learningDate = userSettingQueryService.resolveToday(setting);
        usageQueryService.requireRemainingLimit(
                userId,
                learningDate,
                admin,
                setting
        );
        validateActiveSession(userId);

        SpeakingTopic topic = request.topicId() == null
                ? null
                : topicQueryService.getActiveEntity(request.topicId());
        validateTopicLanguage(topic, setting.getLearningLanguage());

        LearningProfileSummaryDto profile =
                speakingProfileContextService.build(userId);
        List<SelectedKeywordDto> keywords = keywordSelectionFacade.selectForDailySet(
                userId,
                learningDate,
                admin
        );
        SpeakingSessionPolicySnapshot snapshot = snapshotService.create(admin);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        "사용자를 찾을 수 없습니다.",
                        LanguageLearningErrorCode.USER_NOT_FOUND
                ));

        return new SpeakingSessionCreationContext(
                user,
                setting,
                learningDate,
                topic,
                sessionPolicy.resolveStartMode(
                        request.conversationStartMode(),
                        topic == null ? null : topic.getRecommendedStartMode()
                ),
                snapshot,
                profile,
                keywords
        );
    }

    private void validateActiveSession(Long userId) {
        sessionQueryService.findActiveEntity(userId).ifPresent(active -> {
            lifecycleService.expireIfNeeded(active);
            if (active.isActive()) {
                throw new BusinessException(
                        "이미 진행 중인 Speaking Session이 있습니다.",
                        LanguageLearningErrorCode.SESSION_NOT_ACTIVE
                );
            }
        });
    }

    private void validateTopicLanguage(
            SpeakingTopic topic,
            String learningLanguage
    ) {
        if (topic == null || topic.getLearningLanguage() == null) {
            return;
        }
        if (!topic.getLearningLanguage().equalsIgnoreCase(learningLanguage)) {
            throw new BusinessException(
                    "현재 학습 언어에서 사용할 수 없는 Topic입니다.",
                    LanguageLearningErrorCode.SPEAKING_TOPIC_NOT_FOUND
            );
        }
    }
}
