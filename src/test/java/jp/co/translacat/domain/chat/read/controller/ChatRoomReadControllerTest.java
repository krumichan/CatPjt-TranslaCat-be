package jp.co.translacat.domain.chat.read.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.chat.read.dto.request.ChatRoomReadRequestDto;
import jp.co.translacat.domain.chat.read.dto.response.ChatRoomReadResponseDto;
import jp.co.translacat.domain.chat.read.service.ChatRoomReadService;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.global.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatRoomReadControllerTest {

    private ChatRoomReadService chatRoomReadService;
    private ChatRoomReadController chatRoomReadController;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        chatRoomReadService = mock(ChatRoomReadService.class);
        chatRoomReadController = new ChatRoomReadController(
                chatRoomReadService
        );
        objectMapper = new ObjectMapper();

        mockMvc = MockMvcBuilders
                .standaloneSetup(chatRoomReadController)
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver()
                )
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("채팅방 읽음 처리 API는 읽음 커서와 미읽음 수를 반환한다")
    void markAsRead() throws Exception {
        // given
        Long loginUserId = 1L;
        Long chatRoomId = 10L;
        Long lastReadMessageId = 151L;
        LocalDateTime lastReadAt = LocalDateTime.of(
                2026,
                7,
                31,
                15,
                0
        );
        setAuthentication(loginUserId);

        ChatRoomReadRequestDto request =
                new ChatRoomReadRequestDto(lastReadMessageId);
        ChatRoomReadResponseDto response =
                new ChatRoomReadResponseDto(
                        chatRoomId,
                        lastReadMessageId,
                        lastReadAt,
                        0L
                );

        when(chatRoomReadService.markAsRead(
                loginUserId,
                chatRoomId,
                request
        )).thenReturn(response);

        // when & then
        mockMvc.perform(patch(
                        "/api/v1/chat/rooms/{chatRoomId}/read",
                        chatRoomId
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value(200))
                .andExpect(jsonPath("$.body.chatRoomId")
                        .value(chatRoomId))
                .andExpect(jsonPath("$.body.lastReadMessageId")
                        .value(lastReadMessageId))
                .andExpect(jsonPath("$.body.unreadCount").value(0L));

        verify(chatRoomReadService).markAsRead(
                loginUserId,
                chatRoomId,
                request
        );
    }

    @Test
    @DisplayName("마지막 읽은 메시지 ID가 null이면 400을 반환한다")
    void rejectNullLastReadMessageId() throws Exception {
        // given
        setAuthentication(1L);
        ChatRoomReadRequestDto request =
                new ChatRoomReadRequestDto(null);

        // when & then
        mockMvc.perform(patch(
                        "/api/v1/chat/rooms/{chatRoomId}/read",
                        10L
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chatRoomReadService);
    }

    @Test
    @DisplayName("마지막 읽은 메시지 ID가 0 이하이면 400을 반환한다")
    void rejectNonPositiveLastReadMessageId() throws Exception {
        // given
        setAuthentication(1L);
        ChatRoomReadRequestDto request =
                new ChatRoomReadRequestDto(0L);

        // when & then
        mockMvc.perform(patch(
                        "/api/v1/chat/rooms/{chatRoomId}/read",
                        10L
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chatRoomReadService);
    }

    @Test
    @DisplayName("로그인하지 않은 사용자는 읽음 처리할 수 없다")
    void failWhenUnauthenticated() {
        // given
        ChatRoomReadRequestDto request =
                new ChatRoomReadRequestDto(151L);

        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> chatRoomReadController.markAsRead(
                        null,
                        10L,
                        request
                ))
                .satisfies(exception ->
                        org.assertj.core.api.Assertions.assertThat(
                                exception.getErrorCode()
                        ).isEqualTo("UNAUTHORIZED")
                );

        verify(chatRoomReadService, never()).markAsRead(
                anyLong(),
                anyLong(),
                any(ChatRoomReadRequestDto.class)
        );
    }

    private void setAuthentication(Long userId) {
        User user = User.createLocalUser(
                "read-controller@translacat.test",
                "password",
                "readControllerUser",
                Role.USER,
                "READCTRL0001"
        );
        user.setId(userId);

        UserPrincipal userPrincipal = new UserPrincipal(user);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userPrincipal,
                        null,
                        userPrincipal.getAuthorities()
                );

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
