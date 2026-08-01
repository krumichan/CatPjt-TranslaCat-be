package jp.co.translacat.domain.chat.openchat.controller;

import jp.co.translacat.domain.chat.openchat.dto.request.OpenChatBanCreateRequestDto;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatBanActionResponseDto;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatBanListResponseDto;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatMemberProfileResponseDto;
import jp.co.translacat.domain.chat.openchat.service.OpenChatBanQueryService;
import jp.co.translacat.domain.chat.openchat.service.OpenChatModerationService;
import jp.co.translacat.global.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpenChatModerationControllerTest {

    @Mock private OpenChatModerationService moderationService;
    @Mock private OpenChatBanQueryService banQueryService;
    @Mock private UserPrincipal userPrincipal;

    private OpenChatModerationController controller;

    @BeforeEach
    void setUp() {
        controller = new OpenChatModerationController(
                moderationService,
                banQueryService
        );
        when(userPrincipal.getId()).thenReturn(10L);
    }

    @Test
    void delegatesRoleBanListAndReleaseApis() {
        OpenChatBanCreateRequestDto request =
                new OpenChatBanCreateRequestDto(30L, "reason");

        when(moderationService.assignAdmin(10L, 100L, 30L))
                .thenReturn(mock(OpenChatMemberProfileResponseDto.class));
        when(moderationService.revokeAdmin(10L, 100L, 30L))
                .thenReturn(mock(OpenChatMemberProfileResponseDto.class));
        when(moderationService.ban(10L, 100L, request))
                .thenReturn(mock(OpenChatBanActionResponseDto.class));
        when(banQueryService.getActiveBans(
                10L, 100L, "cat", 70L, 20
        )).thenReturn(mock(OpenChatBanListResponseDto.class));
        when(moderationService.release(10L, 100L, 72L))
                .thenReturn(mock(OpenChatBanActionResponseDto.class));

        controller.assignAdmin(userPrincipal, 100L, 30L);
        controller.revokeAdmin(userPrincipal, 100L, 30L);
        controller.ban(userPrincipal, 100L, request);
        controller.getActiveBans(
                userPrincipal,
                100L,
                "cat",
                70L,
                20
        );
        controller.release(userPrincipal, 100L, 72L);

        verify(moderationService).assignAdmin(10L, 100L, 30L);
        verify(moderationService).revokeAdmin(10L, 100L, 30L);
        verify(moderationService).ban(10L, 100L, request);
        verify(banQueryService).getActiveBans(
                10L, 100L, "cat", 70L, 20
        );
        verify(moderationService).release(10L, 100L, 72L);
    }
}
