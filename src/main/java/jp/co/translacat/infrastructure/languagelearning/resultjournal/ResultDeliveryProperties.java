package jp.co.translacat.infrastructure.languagelearning.resultjournal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 기존 평가/조회 경로는 기본적으로 유지한다. 명시적 설정과 Core DDL 준비 뒤에만 수집한다.
 */
@ConfigurationProperties(prefix = "language-learning.result-journal")
public class ResultDeliveryProperties {
    private boolean enabled;
    private boolean deliveryEnabled;
    private String sourceInstanceId;
    private int leaseSeconds = 60;
    private int maximumAttempts = 10;
    private int batchSize = 10;

    public void validate() {
        if (deliveryEnabled && !enabled) throw new IllegalArgumentException("수신 전달 전에 원장 수집을 활성화해야 합니다.");
        if (!enabled) return;
        ResultDeliveryRules.sourceId(sourceInstanceId);
        if (leaseSeconds < 30 || leaseSeconds > 600 || maximumAttempts < 1 || maximumAttempts > 100
                || batchSize < 1 || batchSize > 100) throw new IllegalArgumentException("결과 원장 전달 설정을 확인해 주세요.");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        enabled = value;
    }

    public boolean isDeliveryEnabled() {
        return deliveryEnabled;
    }

    public void setDeliveryEnabled(boolean value) {
        deliveryEnabled = value;
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
}
