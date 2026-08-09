package jp.co.translacat.domain.chat.ai.service;

import jp.co.translacat.domain.chat.ai.entity.ChatAiAgent;
import jp.co.translacat.domain.user.profile.storage.port.ImageStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatAiProfileImageUrlResolver {

    private final ImageStoragePort imageStoragePort;

    public String resolveProfileImageUrl(ChatAiAgent agent) {
        return resolve(agent.getProfileImageObjectKey());
    }

    public String resolveProfileBackgroundImageUrl(ChatAiAgent agent) {
        return resolve(agent.getProfileBackgroundImageObjectKey());
    }

    private String resolve(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        return imageStoragePort.resolvePublicUrl(objectKey);
    }
}
