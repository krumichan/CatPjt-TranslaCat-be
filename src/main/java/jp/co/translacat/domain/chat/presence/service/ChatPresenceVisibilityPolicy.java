package jp.co.translacat.domain.chat.presence.service;

import jp.co.translacat.domain.chat.ai.enums.ChatAiDisclosureType;
import jp.co.translacat.domain.chat.ai.repository.ChatRoomAiSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatPresenceVisibilityPolicy {

    private final ChatRoomAiSettingRepository chatRoomAiSettingRepository;

    /**
     * PRIVATE AI rooms hide all human-member Presence indicators.
     */
    public boolean isVisible(Long roomId) {
        if (roomId == null || roomId <= 0) {
            return false;
        }

        return chatRoomAiSettingRepository
                .findByChatRoomId(roomId)
                .map(setting -> setting.getDisclosureType()
                        != ChatAiDisclosureType.PRIVATE)
                .orElse(true);
    }
}
