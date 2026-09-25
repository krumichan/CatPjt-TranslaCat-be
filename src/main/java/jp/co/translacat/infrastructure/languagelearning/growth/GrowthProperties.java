package jp.co.translacat.infrastructure.languagelearning.growth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.UUID;

@ConfigurationProperties(prefix = "language-learning.growth")
public class GrowthProperties {
    private boolean enabled;
    private String sourceInstanceId;
    private int leaseSeconds = 60;
    private int maximumAttempts = 10;
    private int batchSize = 20;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        enabled = value;
    }

    public String getSourceInstanceId() {
        return sourceInstanceId;
    }

    public void setSourceInstanceId(String value) {
        sourceInstanceId = value;
    }

    public int getLeaseSeconds() {
        return leaseSeconds;
    }

    public void setLeaseSeconds(int value) {
        leaseSeconds = value;
    }

    public int getMaximumAttempts() {
        return maximumAttempts;
    }

    public void setMaximumAttempts(int value) {
        maximumAttempts = value;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int value) {
        batchSize = value;
    }

    public void validate() {
        if (!enabled) return;
        if (sourceInstanceId == null || !UUID.fromString(sourceInstanceId).toString().equals(sourceInstanceId))
            throw new IllegalArgumentException("성장 전달에는 고정된 source-instance-id UUID가 필요합니다.");
        if (leaseSeconds < 30
                || leaseSeconds > 300
                || maximumAttempts < 1
                || maximumAttempts > 20
                || batchSize < 1
                || batchSize > 100)
            throw new IllegalArgumentException("성장 전달 설정 범위를 확인해 주세요.");
    }
}
