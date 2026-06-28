package jp.co.translacat.domain.chat.room.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.chat.room.dto.request.FriendGroupChatRoomCreateRequestDto;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatFriendGroupRoomControllerTest {

    private ChatRoomFacade chatRoomFacade;
    private ChatFriendRoomController chatFriendRoomController;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        chatRoomFacade = mock(ChatRoomFacade.class);
        chatFriendRoomController = new ChatFriendRoomController(chatRoomFacade);

        mockMvc = MockMvcBuilders
                .standaloneSetup(chatFriendRoomController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        objectMapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("친구 그룹 채팅방 생성 API 테스트")
    void createFriendGroupRoom() throws Exception {
        // given
        Long loginUserId = 1L;
        setAuthentication(loginUserId);

        FriendGroupChatRoomCreateRequestDto request = new FriendGroupChatRoomCreateRequestDto(
                "친구 그룹 채팅",
                "친구들과 이야기하는 채팅방",
                List.of(2L, 3L)
        );

        ChatRoomResponseDto response = new ChatRoomResponseDto(
                100L,
                ChatRoomType.GROUP,
                ChatRoomSourceType.FRIEND,
                "친구 그룹 채팅",
                "친구들과 이야기하는 채팅방",
                loginUserId,
                3L,
                true,
                null,
                null,
                false,
                null,
                null
        );

        when(chatRoomFacade.createFriendGroupRoom(loginUserId, request)).thenReturn(response);

        // when & then
        mockMvc.perform(post("/api/v1/chat/friends/group-rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value(200))
                .andExpect(jsonPath("$.body.id").value(100L))
                .andExpect(jsonPath("$.body.roomType").value("GROUP"))
                .andExpect(jsonPath("$.body.sourceType").value("FRIEND"))
                .andExpect(jsonPath("$.body.name").value("친구 그룹 채팅"))
                .andExpect(jsonPath("$.body.memberCount").value(3L));

        verify(chatRoomFacade).createFriendGroupRoom(loginUserId, request);
    }

    @Test
    @DisplayName("로그인하지 않은 사용자는 친구 그룹 채팅방을 생성할 수 없다")
    void failWhenUnauthenticated() {
        // given
        FriendGroupChatRoomCreateRequestDto request = new FriendGroupChatRoomCreateRequestDto(
                "친구 그룹 채팅",
                null,
                List.of(2L, 3L)
        );

        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> chatFriendRoomController.createFriendGroupRoom(null, request))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("UNAUTHORIZED")
                );

        verify(chatRoomFacade, never()).createFriendGroupRoom(anyLong(), any(FriendGroupChatRoomCreateRequestDto.class));
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
