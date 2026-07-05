package jp.co.translacat.domain.user.profile.storage.service;

import jp.co.translacat.domain.user.profile.entity.UserProfile;
import jp.co.translacat.domain.user.profile.storage.port.ImageStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserProfileImageUrlResolver {

    private final ImageStoragePort imageStoragePort;

    public String resolveProfileImageUrl(UserProfile userProfile) {
        return resolve(userProfile.getProfileImageObjectKey());
    }

    public String resolveProfileBackgroundImageUrl(UserProfile userProfile) {
        return resolve(userProfile.getProfileBackgroundImageObjectKey());
    }

    private String resolve(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }

        return imageStoragePort.resolvePublicUrl(objectKey);
    }
}
