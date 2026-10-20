package jp.co.translacat.domain.user.pushinstallation;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ConfigurationProperties("push.installations")
public class PushInstallationProperties {
    private boolean enabled;
    private int maximumActivePerUser = 10;
    private Map<String, List<String>> allowedProjects = new LinkedHashMap<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getMaximumActivePerUser() { return maximumActivePerUser; }
    public void setMaximumActivePerUser(int value) { maximumActivePerUser = value; }
    public Map<String, List<String>> getAllowedProjects() { return allowedProjects; }
    public void setAllowedProjects(Map<String, List<String>> value) { allowedProjects = value; }

    public void requireConfigured() {
        // 기본값은 닫혀 있다. 실제 환경/프로젝트를 추정해 허용하지 않는다.
        if (!enabled || maximumActivePerUser < 1 || maximumActivePerUser > 10
                || allowedProjects == null || allowedProjects.isEmpty()) throw notConfigured();
        for (var entry : allowedProjects.entrySet()) {
            if (entry.getKey() == null || !Set.of("development", "preview", "production").contains(entry.getKey())
                    || entry.getValue() == null || entry.getValue().isEmpty()
                    || entry.getValue().stream().anyMatch(value -> !PushInstallationValidation.isUuid(value))) {
                throw notConfigured();
            }
        }
    }

    public void requireAllowed(String environment, String projectId) {
        requireConfigured();
        if (!allowedProjects.getOrDefault(environment, List.of()).contains(projectId)) {
            throw new PushInstallationException(org.springframework.http.HttpStatus.BAD_REQUEST, "PUSH_TARGET_NOT_ALLOWED");
        }
    }

    private PushInstallationException notConfigured() {
        return new PushInstallationException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "PUSH_NOT_CONFIGURED");
    }
}
