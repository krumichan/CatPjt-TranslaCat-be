package jp.co.translacat.domain.languagelearning.speaking.session.model;

import jp.co.translacat.domain.languagelearning.ai.dto.model.LearningProfileSummaryDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.SelectedKeywordDto;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningUserSetting;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.ConversationStartMode;
import jp.co.translacat.domain.languagelearning.speaking.topic.entity.SpeakingTopic;
import jp.co.translacat.domain.user.entity.User;

import java.time.LocalDate;
import java.util.List;

public record SpeakingSessionCreationContext(
        User user,
        LanguageLearningUserSetting userSetting,
        LocalDate learningDate,
        SpeakingTopic topic,
        ConversationStartMode resolvedStartMode,
        SpeakingSessionPolicySnapshot policySnapshot,
        LearningProfileSummaryDto learningProfile,
        List<SelectedKeywordDto> selectedKeywords
) {
}
