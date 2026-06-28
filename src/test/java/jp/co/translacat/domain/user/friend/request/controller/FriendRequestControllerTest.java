package jp.co.translacat.domain.user.friend.request.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.friend.request.dto.FriendRequestResponseDto;
import jp.co.translacat.domain.user.friend.request.dto.FriendRequestSendRequestDto;
import jp.co.translacat.domain.user.friend.request.enums.FriendRequestStatus;
import jp.co.translacat.domain.user.friend.request.service.FriendRequestService;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FriendRequestControllerTest {

    private FriendRequestService friendRequestService;
    private FriendRequestController friendRequestController;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        friendRequestService = mock(FriendRequestService.class);
        friendRequestController = new FriendRequestController(friendRequestService);
        objectMapper = new ObjectMapper();

        mockMvc = MockMvcBuilders
                .standaloneSetup(friendRequestController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("친구 요청 전송 API 테스트")
    void sendFriendRequest() throws Exception {
        // given
        Long loginUserId = 1L;
        setAuthentication(loginUserId);

        FriendRequestSendRequestDto request = new FriendRequestSendRequestDto("TCAT-00000002");

        FriendRequestResponseDto response = new FriendRequestResponseDto(
                100L,
                1L,
                2L,
                "TCAT-00000002",
                "receiver",
                FriendRequestStatus.PENDING,
                LocalDateTime.of(2026, 6, 28, 10, 0),
                null
        );

        when(friendRequestService.sendFriendRequest(eq(loginUserId), any(FriendRequestSendRequestDto.class)))
                .thenReturn(response);

        // when & then
        mockMvc.perform(
                        post("/api/v1/friend-requests")
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value(200))
                .andExpect(jsonPath("$.body.id").value(100L))
                .andExpect(jsonPath("$.body.requesterUserId").value(1L))
                .andExpect(jsonPath("$.body.receiverUserId").value(2L))
                .andExpect(jsonPath("$.body.receiverPublicId").value("TCAT-00000002"))
                .andExpect(jsonPath("$.body.receiverNickname").value("receiver"))
                .andExpect(jsonPath("$.body.status").value("PENDING"));

        verify(friendRequestService).sendFriendRequest(eq(loginUserId), any(FriendRequestSendRequestDto.class));
    }

    @Test
    @DisplayName("로그인하지 않은 사용자는 친구 요청을 전송할 수 없다")
    void failWhenUnauthenticated() {
        // given
        FriendRequestSendRequestDto request = new FriendRequestSendRequestDto("TCAT-00000002");

        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> friendRequestController.sendFriendRequest(null, request))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("UNAUTHORIZED")
                );

        verify(friendRequestService, never()).sendFriendRequest(
                anyLong(),
                any(FriendRequestSendRequestDto.class)
        );
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
