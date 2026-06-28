package jp.co.translacat.domain.chat.room.controller;

import jp.co.translacat.domain.chat.room.dto.response.ChatRoomResponseDto;
import jp.co.translacat.domain.chat.room.enums.ChatRoomSourceType;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;
import jp.co.translacat.domain.chat.room.facade.ChatRoomFacade;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.global.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatFriendRoomControllerTest {

    private ChatRoomFacade chatRoomFacade;
    private ChatFriendRoomController chatFriendRoomController;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        chatRoomFacade = mock(ChatRoomFacade.class);
        chatFriendRoomController = new ChatFriendRoomController(chatRoomFacade);

        mockMvc = MockMvcBuilders
                .standaloneSetup(chatFriendRoomController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("친구 1:1 채팅 시작 API 테스트")
    void createOrGetFriendDirectRoom() throws Exception {
        // given
        Long loginUserId = 1L;
        Long friendUserId = 2L;
        setAuthentication(loginUserId);

        ChatRoomResponseDto response = new ChatRoomResponseDto(
                100L,
                ChatRoomType.DIRECT,
                ChatRoomSourceType.FRIEND,
                null,
                null,
                loginUserId,
                2L,
                true,
                null,
                null,
                false,
                null,
                null
        );

        when(chatRoomFacade.createOrGetFriendDirectRoom(loginUserId, friendUserId)).thenReturn(response);

        // when & then
        mockMvc.perform(post("/api/v1/chat/friends/{friendUserId}/direct-room", friendUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value(200))
                .andExpect(jsonPath("$.body.id").value(100L))
                .andExpect(jsonPath("$.body.roomType").value("DIRECT"))
                .andExpect(jsonPath("$.body.sourceType").value("FRIEND"))
                .andExpect(jsonPath("$.body.memberCount").value(2L));

        verify(chatRoomFacade).createOrGetFriendDirectRoom(loginUserId, friendUserId);
    }

    @Test
    @DisplayName("로그인하지 않은 사용자는 친구 1:1 채팅을 시작할 수 없다")
    void failWhenUnauthenticated() {
        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> chatFriendRoomController.createOrGetFriendDirectRoom(null, 2L))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("UNAUTHORIZED")
                );

        verify(chatRoomFacade, never()).createOrGetFriendDirectRoom(anyLong(), anyLong());
    }

    private void setAuthentication(Long userId) {
        User user = User.createLocalUser(
                "test@example.com",
                "password",
                "testUser",
                Role.USER,
                "TCAT-00000001"
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
