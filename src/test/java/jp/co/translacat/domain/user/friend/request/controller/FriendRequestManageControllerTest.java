package jp.co.translacat.domain.user.friend.request.controller;

import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.friend.request.dto.FriendRequestListItemResponseDto;
import jp.co.translacat.domain.user.friend.request.enums.FriendRequestStatus;
import jp.co.translacat.domain.user.friend.request.service.FriendRequestService;
import jp.co.translacat.domain.user.profile.dto.UserSummaryProfileResponseDto;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FriendRequestManageControllerTest {

    private FriendRequestService friendRequestService;
    private FriendRequestController friendRequestController;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        friendRequestService = mock(FriendRequestService.class);
        friendRequestController = new FriendRequestController(friendRequestService);

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
    @DisplayName("받은 친구 요청 목록 조회 API 테스트")
    void getReceivedPendingRequests() throws Exception {
        // given
        Long loginUserId = 2L;
        setAuthentication(loginUserId);

        when(friendRequestService.getReceivedPendingRequests(loginUserId)).thenReturn(List.of(createResponse()));

        // when & then
        mockMvc.perform(get("/api/v1/friend-requests/received"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value(200))
                .andExpect(jsonPath("$.body[0].requester.userId").value(1L))
                .andExpect(jsonPath("$.body[0].receiver.userId").value(2L))
                .andExpect(jsonPath("$.body[0].status").value("PENDING"));

        verify(friendRequestService).getReceivedPendingRequests(loginUserId);
    }

    @Test
    @DisplayName("보낸 친구 요청 목록 조회 API 테스트")
    void getSentPendingRequests() throws Exception {
        // given
        Long loginUserId = 1L;
        setAuthentication(loginUserId);

        when(friendRequestService.getSentPendingRequests(loginUserId)).thenReturn(List.of(createResponse()));

        // when & then
        mockMvc.perform(get("/api/v1/friend-requests/sent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value(200))
                .andExpect(jsonPath("$.body[0].requester.userId").value(1L))
                .andExpect(jsonPath("$.body[0].receiver.userId").value(2L))
                .andExpect(jsonPath("$.body[0].status").value("PENDING"));

        verify(friendRequestService).getSentPendingRequests(loginUserId);
    }

    @Test
    @DisplayName("친구 요청 수락 API 테스트")
    void acceptFriendRequest() throws Exception {
        // given
        Long loginUserId = 2L;
        Long requestId = 100L;
        setAuthentication(loginUserId);

        FriendRequestListItemResponseDto response = createResponse(FriendRequestStatus.ACCEPTED);
        when(friendRequestService.acceptFriendRequest(loginUserId, requestId)).thenReturn(response);

        // when & then
        mockMvc.perform(patch("/api/v1/friend-requests/{requestId}/accept", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value(200))
                .andExpect(jsonPath("$.body.status").value("ACCEPTED"));

        verify(friendRequestService).acceptFriendRequest(loginUserId, requestId);
    }

    @Test
    @DisplayName("친구 요청 거절 API 테스트")
    void rejectFriendRequest() throws Exception {
        // given
        Long loginUserId = 2L;
        Long requestId = 100L;
        setAuthentication(loginUserId);

        FriendRequestListItemResponseDto response = createResponse(FriendRequestStatus.REJECTED);
        when(friendRequestService.rejectFriendRequest(loginUserId, requestId)).thenReturn(response);

        // when & then
        mockMvc.perform(patch("/api/v1/friend-requests/{requestId}/reject", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value(200))
                .andExpect(jsonPath("$.body.status").value("REJECTED"));

        verify(friendRequestService).rejectFriendRequest(loginUserId, requestId);
    }

    @Test
    @DisplayName("친구 요청 취소 API 테스트")
    void cancelFriendRequest() throws Exception {
        // given
        Long loginUserId = 1L;
        Long requestId = 100L;
        setAuthentication(loginUserId);

        FriendRequestListItemResponseDto response = createResponse(FriendRequestStatus.CANCELED);
        when(friendRequestService.cancelFriendRequest(loginUserId, requestId)).thenReturn(response);

        // when & then
        mockMvc.perform(patch("/api/v1/friend-requests/{requestId}/cancel", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value(200))
                .andExpect(jsonPath("$.body.status").value("CANCELED"));

        verify(friendRequestService).cancelFriendRequest(loginUserId, requestId);
    }

    @Test
    @DisplayName("로그인하지 않은 사용자는 받은 친구 요청 목록을 조회할 수 없다")
    void failWhenUnauthenticated() {
        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> friendRequestController.getReceivedPendingRequests(null))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("UNAUTHORIZED")
                );

        verify(friendRequestService, never()).getReceivedPendingRequests(anyLong());
    }

    private FriendRequestListItemResponseDto createResponse() {
        return createResponse(FriendRequestStatus.PENDING);
    }

    private FriendRequestListItemResponseDto createResponse(FriendRequestStatus status) {
        return new FriendRequestListItemResponseDto(
                100L,
                new UserSummaryProfileResponseDto(1L, "TCAT-00000001", "requester", null),
                new UserSummaryProfileResponseDto(2L, "TCAT-00000002", "receiver", null),
                status,
                LocalDateTime.of(2026, 6, 28, 10, 0),
                status == FriendRequestStatus.PENDING ? null : LocalDateTime.of(2026, 6, 28, 10, 5)
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
