package jp.co.translacat.domain.languagelearning.speaking.session.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpeakingSessionPolicySnapshotTest {

    @Test
    void convertsConfiguredMinutesToSeconds() {
        SpeakingSessionPolicySnapshot snapshot = snapshot();

        assertThat(snapshot.maxSessionSeconds()).isEqualTo(600);
        assertThat(snapshot.dailySpeakingHardLimitSeconds()).isEqualTo(1800);
    }

    @Test
    void keepsAudioAndRetryPolicyInSnapshot() {
        SpeakingSessionPolicySnapshot snapshot = snapshot();

        assertThat(snapshot.minValidAudioSeconds()).isEqualTo(1.0);
        assertThat(snapshot.maxTurnAudioSeconds()).isEqualTo(60);
        assertThat(snapshot.maxAudioFileBytes()).isEqualTo(10L * 1024L * 1024L);
        assertThat(snapshot.rawAudioRetentionDays()).isEqualTo(7);
        assertThat(snapshot.reportedAudioRetentionDays()).isEqualTo(30);
        assertThat(snapshot.automaticRetryLimitPerStage()).isEqualTo(2);
        assertThat(snapshot.manualRetryLimitPerStage()).isEqualTo(1);
    }

    private SpeakingSessionPolicySnapshot snapshot() {
        return new SpeakingSessionPolicySnapshot(
                true,
                30,
                5,
                10,
                20,
                1.0,
                60,
                10L * 1024L * 1024L,
                7,
                30,
                2,
                2,
                1,
                30,
                30,
                60
        );
    }
}
