package jp.co.translacat.domain.chat.openchat.profile.service;

import jp.co.translacat.domain.chat.openchat.support.OpenChatPolicy;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class OpenChatProfileImageKeyFactory {

    public String create(Long openChatMemberId, String extension) {
        return "%s%s.%s".formatted(
                OpenChatPolicy.profileImageObjectKeyPrefix(
                        openChatMemberId
                ),
                UUID.randomUUID(),
                extension
        );
    }
}
