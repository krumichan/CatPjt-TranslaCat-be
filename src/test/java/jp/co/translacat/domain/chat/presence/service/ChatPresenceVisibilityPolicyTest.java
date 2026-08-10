package jp.co.translacat.domain.chat.presence.service;

import jp.co.translacat.domain.chat.ai.entity.ChatRoomAiSetting;
import jp.co.translacat.domain.chat.ai.enums.ChatAiDisclosureType;
import jp.co.translacat.domain.chat.ai.repository.ChatRoomAiSettingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatPresenceVisibilityPolicyTest {

    @Mock private ChatRoomAiSettingRepository chatRoomAiSettingRepository;

    @Test
    void isVisible_HidesPrivateAiRoom() {
        ChatRoomAiSetting setting = mock(ChatRoomAiSetting.class);
        when(setting.getDisclosureType()).thenReturn(ChatAiDisclosureType.PRIVATE);
        when(chatRoomAiSettingRepository.findByChatRoomId(10L))
                .thenReturn(Optional.of(setting));

        ChatPresenceVisibilityPolicy policy =
                new ChatPresenceVisibilityPolicy(chatRoomAiSettingRepository);

        assertThat(policy.isVisible(10L)).isFalse();
    }

    @Test
    void isVisible_AllowsRoomWithoutPrivateSetting() {
        when(chatRoomAiSettingRepository.findByChatRoomId(10L))
                .thenReturn(Optional.empty());

        ChatPresenceVisibilityPolicy policy =
                new ChatPresenceVisibilityPolicy(chatRoomAiSettingRepository);

        assertThat(policy.isVisible(10L)).isTrue();
    }
}
