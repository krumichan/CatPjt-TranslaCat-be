package jp.co.translacat.domain.user.profile.storage.model;

import java.util.UUID;

public enum ProfileImageType {

    PROFILE("profile", 5L * 1024 * 1024),
    BACKGROUND("background", 10L * 1024 * 1024);

    private final String pathSegment;
    private final long maxBytes;

    ProfileImageType(String pathSegment, long maxBytes) {
        this.pathSegment = pathSegment;
        this.maxBytes = maxBytes;
    }

    public long getMaxBytes() {
        return maxBytes;
    }

    public String createObjectKey(Long userId, String extension) {
        return "user-profile/%d/%s/%s.%s".formatted(
                userId,
                pathSegment,
                UUID.randomUUID(),
                extension
        );
    }
}
