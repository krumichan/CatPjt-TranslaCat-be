package jp.co.translacat.domain.chat.openchat.profile.service;

import jp.co.translacat.domain.user.profile.storage.port.ImageStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OpenChatProfileImageUrlResolver {

    private final ImageStoragePort imageStoragePort;

    public String resolve(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        return imageStoragePort.resolvePublicUrl(objectKey);
    }
}
