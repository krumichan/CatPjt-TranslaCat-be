package jp.co.translacat.domain.languagelearning.speaking.session.service;

import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningAdminSetting;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.session.model.SpeakingSessionPolicySnapshot;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SpeakingSessionPolicySnapshotService {

    private final LanguageLearningJsonCodec jsonCodec;

    public SpeakingSessionPolicySnapshot create(
            LanguageLearningAdminSetting admin
    ) {
        return new SpeakingSessionPolicySnapshot(
                admin.isSpeakingEvaluationEnabled(),
                admin.getDailySpeakingHardLimitMinutes(),
                admin.getDailySpeakingSessionLimit(),
                admin.getMaxSessionMinutes(),
                admin.getMaxTurnsPerSession(),
                admin.getMinValidAudioSeconds(),
                admin.getMaxTurnAudioSeconds(),
                admin.getMaxAudioFileBytes(),
                admin.getRawAudioRetentionDays(),
                admin.getReportedAudioRetentionDays(),
                admin.getActiveSessionResumeHours(),
                admin.getAutomaticRetryLimitPerStage(),
                admin.getManualRetryLimitPerStage(),
                admin.getSttTimeoutSeconds(),
                admin.getTtsTimeoutSeconds(),
                admin.getEvaluationTimeoutSeconds()
        );
    }

    public SpeakingSessionPolicySnapshot read(SpeakingSession session) {
        return jsonCodec.read(
                session.getPolicySnapshotJson(),
                SpeakingSessionPolicySnapshot.class
        );
    }
}
