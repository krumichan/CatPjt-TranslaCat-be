package jp.co.translacat.domain.user.pushinstallation;

import java.util.List;

public final class PushInstallationDtos {
    private PushInstallationDtos() {}

    public record Actor(long userId, String sid) {
        @Override public String toString() { return "PushActor[REDACTED]"; }
    }

    public record Put(String provider, String expoPushToken, String platform, String appEnvironment,
                      String projectId, String installationProof, long generation) {
        @Override public String toString() { return "PushInstallationPut[REDACTED]"; }
    }

    public record Delete(String installationProof, long generation) {
        @Override public String toString() { return "PushInstallationDelete[REDACTED]"; }
    }

    public record Registered(String installationId, String accountBindingId, long generation, long tokenVersion) {}

    public record Target(long userId, String installationId, String accountBindingId, long generation,
                         long tokenVersion, String expoPushToken, String platform) {
        @Override public String toString() { return "PushInstallationTarget[REDACTED]"; }
    }

    public record Targets(List<Target> targets) {
        @Override public String toString() { return "PushInstallationTargets[REDACTED]"; }
    }

    public record Invalidate(String installationId, String accountBindingId, long generation, long tokenVersion) {}
}
