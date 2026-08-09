package jp.co.translacat.domain.chat.ai.service;

import jp.co.translacat.domain.user.profile.storage.model.ProfileImageType;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ChatAiProfileImageKeyFactory {

    public String create(
            Long aiMemberId,
            ProfileImageType imageType,
            String extension
    ) {
        String type = imageType == ProfileImageType.PROFILE
                ? "profile"
                : "background";
        return "chat-ai/%d/%s/%s.%s".formatted(
                aiMemberId,
                type,
                UUID.randomUUID(),
                extension
        );
    }

    public boolean belongsToMember(Long aiMemberId, String objectKey) {
        if (aiMemberId == null || objectKey == null) {
            return false;
        }
        return objectKey.startsWith("chat-ai/%d/".formatted(aiMemberId));
    }
}
