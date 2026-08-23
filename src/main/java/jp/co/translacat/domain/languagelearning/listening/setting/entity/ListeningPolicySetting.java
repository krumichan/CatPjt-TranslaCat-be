package jp.co.translacat.domain.languagelearning.listening.setting.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "language_learning_listening_policy_setting")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ListeningPolicySetting extends BaseAuditable {

    public static final String DEFAULT_ID = "DEFAULT";
    public static final String PROFILE_POLICY_VERSION =
            "listening-profile-v1";
    public static final String MODEL_CONFIG_VERSION =
            "listening-model-config-v1";

    @Id
    @Column(length = 30)
    private String id;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "default_item_count", nullable = false)
    private int defaultItemCount;

    @Column(name = "min_item_count", nullable = false)
    private int minItemCount;

    @Column(name = "max_item_count", nullable = false)
    private int maxItemCount;

    @Column(name = "hard_item_limit", nullable = false)
    private int hardItemLimit;

    @Column(name = "reference_audio_max_seconds", nullable = false)
    private int referenceAudioMaxSeconds;

    @Column(name = "repeat_audio_max_seconds", nullable = false)
    private int repeatAudioMaxSeconds;

    @Column(name = "max_audio_file_bytes", nullable = false)
    private long maxAudioFileBytes;

    @Column(name = "max_rerecord_count", nullable = false)
    private int maxRerecordCount;

    @Column(name = "resume_hours", nullable = false)
    private int resumeHours;

    @Column(name = "reference_audio_retention_days", nullable = false)
    private int referenceAudioRetentionDays;

    @Column(name = "user_audio_retention_days", nullable = false)
    private int userAudioRetentionDays;

    @Column(name = "reported_audio_retention_days", nullable = false)
    private int reportedAudioRetentionDays;

    @Column(name = "automatic_retry_limit", nullable = false)
    private int automaticRetryLimit;

    @Column(name = "manual_retry_limit", nullable = false)
    private int manualRetryLimit;

    @Column(name = "practice_attempt_limit", nullable = false)
    private int practiceAttemptLimit;

    @Column(name = "profile_policy_version", nullable = false, length = 100)
    private String profilePolicyVersion;

    @Column(name = "model_config_version", nullable = false, length = 100)
    private String modelConfigVersion;

    @Column(name = "reference_tts_regeneration_enabled", nullable = false)
    private boolean referenceTtsRegenerationEnabled;

    private ListeningPolicySetting(String id) {
        this.id = id;
        this.enabled = true;
        this.defaultItemCount = 5;
        this.minItemCount = 1;
        this.maxItemCount = 20;
        this.hardItemLimit = 30;
        this.referenceAudioMaxSeconds = 30;
        this.repeatAudioMaxSeconds = 60;
        this.maxAudioFileBytes = 10L * 1024L * 1024L;
        this.maxRerecordCount = 2;
        this.resumeHours = 2;
        this.referenceAudioRetentionDays = 7;
        this.userAudioRetentionDays = 7;
        this.reportedAudioRetentionDays = 30;
        this.automaticRetryLimit = 2;
        this.manualRetryLimit = 1;
        this.practiceAttemptLimit = 1;
        this.profilePolicyVersion = PROFILE_POLICY_VERSION;
        this.modelConfigVersion = MODEL_CONFIG_VERSION;
        this.referenceTtsRegenerationEnabled = false;
    }

    public static ListeningPolicySetting createDefault() {
        return new ListeningPolicySetting(DEFAULT_ID);
    }

    public int resolveItemCount(Integer requested) {
        int value = requested == null ? defaultItemCount : requested;
        if (value < minItemCount || value > maxItemCount
                || value > hardItemLimit) {
            throw new IllegalArgumentException(
                    "Listening Item Count가 허용 범위를 벗어났습니다."
            );
        }
        return value;
    }
}
