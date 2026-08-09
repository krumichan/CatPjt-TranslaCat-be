package jp.co.translacat.domain.chat.ai.controller;

import jp.co.translacat.domain.chat.ai.dto.request.ChatAiMemberCreateRequestDto;
import jp.co.translacat.domain.chat.ai.dto.request.ChatAiMemberUpdateRequestDto;
import jp.co.translacat.domain.chat.ai.dto.response.ChatAiMemberListResponseDto;
import jp.co.translacat.domain.chat.ai.dto.response.ChatAiMemberResponseDto;
import jp.co.translacat.domain.chat.ai.dto.response.ChatAiSafeProfileResponseDto;
import jp.co.translacat.domain.chat.ai.service.ChatAiDisplayMemberService;
import jp.co.translacat.domain.chat.ai.service.ChatAiMemberService;
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
class ChatAiMemberControllerTest {

    @Mock private ChatAiMemberService service;
    @Mock private ChatAiDisplayMemberService displayMemberService;
    @Mock private UserPrincipal userPrincipal;

    private ChatAiMemberController controller;

    @BeforeEach
    void setUp() {
        controller = new ChatAiMemberController(
                service,
                displayMemberService
        );
        when(userPrincipal.getId()).thenReturn(10L);
    }

    @Test
    void delegatesCrudApis() {
        ChatAiMemberCreateRequestDto createRequest =
                new ChatAiMemberCreateRequestDto(
                        "Mika",
                        null,
                        "ja",
                        "persona"
                );
        ChatAiMemberUpdateRequestDto updateRequest =
                new ChatAiMemberUpdateRequestDto(
                        "Mika2",
                        "bio",
                        "ko",
                        "persona2"
                );

        when(service.getMembers(10L, 100L))
                .thenReturn(mock(ChatAiMemberListResponseDto.class));
        when(service.create(10L, 100L, createRequest))
                .thenReturn(mock(ChatAiMemberResponseDto.class));
        when(service.getMember(10L, 100L, 30L))
                .thenReturn(mock(ChatAiMemberResponseDto.class));
        when(displayMemberService.getSafeProfile(10L, 100L, 30L))
                .thenReturn(mock(ChatAiSafeProfileResponseDto.class));
        when(service.update(10L, 100L, 30L, updateRequest))
                .thenReturn(mock(ChatAiMemberResponseDto.class));
        when(service.delete(10L, 100L, 30L))
                .thenReturn(mock(ChatAiMemberResponseDto.class));

        controller.getMembers(userPrincipal, 100L);
        controller.create(userPrincipal, 100L, createRequest);
        controller.getMember(userPrincipal, 100L, 30L);
        controller.getMemberProfile(userPrincipal, 100L, 30L);
        controller.update(userPrincipal, 100L, 30L, updateRequest);
        controller.delete(userPrincipal, 100L, 30L);

        verify(service).getMembers(10L, 100L);
        verify(service).create(10L, 100L, createRequest);
        verify(service).getMember(10L, 100L, 30L);
        verify(displayMemberService).getSafeProfile(10L, 100L, 30L);
        verify(service).update(10L, 100L, 30L, updateRequest);
        verify(service).delete(10L, 100L, 30L);
    }
}
