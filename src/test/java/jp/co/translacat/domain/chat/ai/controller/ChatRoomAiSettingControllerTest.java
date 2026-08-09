package jp.co.translacat.domain.chat.ai.controller;

import jp.co.translacat.domain.chat.ai.dto.request.ChatRoomAiSettingUpdateRequestDto;
import jp.co.translacat.domain.chat.ai.dto.response.ChatRoomAiSettingResponseDto;
import jp.co.translacat.domain.chat.ai.enums.ChatAiDisclosureType;
import jp.co.translacat.domain.chat.ai.enums.ChatAiMentionPermission;
import jp.co.translacat.domain.chat.ai.service.ChatRoomAiSettingService;
import jp.co.translacat.global.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatRoomAiSettingControllerTest {

    @Mock private ChatRoomAiSettingService service;
    @Mock private UserPrincipal userPrincipal;

    private ChatRoomAiSettingController controller;

    @BeforeEach
    void setUp() {
        controller = new ChatRoomAiSettingController(service);
        when(userPrincipal.getId()).thenReturn(10L);
    }

    @Test
    void delegatesGetAndPatch() {
        ChatRoomAiSettingUpdateRequestDto request =
                new ChatRoomAiSettingUpdateRequestDto(
                        ChatAiDisclosureType.PRIVATE,
                        ChatAiMentionPermission.OWNER_ADMIN_ONLY,
                        false,
                        false
                );
        when(service.getSettings(10L, 100L))
                .thenReturn(mock(ChatRoomAiSettingResponseDto.class));
        when(service.updateSettings(10L, 100L, request))
                .thenReturn(mock(ChatRoomAiSettingResponseDto.class));

        controller.getSettings(userPrincipal, 100L);
        controller.updateSettings(userPrincipal, 100L, request);

        verify(service).getSettings(10L, 100L);
        verify(service).updateSettings(10L, 100L, request);
    }
}
