package jp.co.translacat.domain.user.friend.controller;

import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.friend.dto.FriendResponseDto;
import jp.co.translacat.domain.user.friend.service.FriendService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FriendControllerTest {

    private FriendService friendService;
    private FriendController friendController;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        friendService = mock(FriendService.class);
        friendController = new FriendController(friendService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(friendController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("친구 목록 조회 API 테스트")
    void getFriends() throws Exception {
        // given
        Long loginUserId = 1L;
        setAuthentication(loginUserId);

        FriendResponseDto response = new FriendResponseDto(
                100L,
                new UserSummaryProfileResponseDto(
                        2L,
                        "TCAT-00000002",
                        "friendUser",
                        null
                ),
                LocalDateTime.of(2026, 6, 28, 10, 0)
        );

        when(friendService.getFriends(loginUserId)).thenReturn(List.of(response));

        // when & then
        mockMvc.perform(get("/api/v1/friends"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value(200))
                .andExpect(jsonPath("$.body[0].id").value(100L))
                .andExpect(jsonPath("$.body[0].friend.userId").value(2L))
                .andExpect(jsonPath("$.body[0].friend.publicId").value("TCAT-00000002"))
                .andExpect(jsonPath("$.body[0].friend.nickname").value("friendUser"));

        verify(friendService).getFriends(loginUserId);
    }

    @Test
    @DisplayName("친구 삭제 API 테스트")
    void deleteFriend() throws Exception {
        // given
        Long loginUserId = 1L;
        Long friendUserId = 2L;
        setAuthentication(loginUserId);

        // when & then
        mockMvc.perform(delete("/api/v1/friends/{friendUserId}", friendUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value(200))
                .andExpect(jsonPath("$.body").value(true));

        verify(friendService).deleteFriend(loginUserId, friendUserId);
    }

    @Test
    @DisplayName("로그인하지 않은 사용자는 친구 목록을 조회할 수 없다")
    void failWhenUnauthenticatedGetFriends() {
        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> friendController.getFriends(null))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("UNAUTHORIZED")
                );

        verify(friendService, never()).getFriends(anyLong());
    }

    @Test
    @DisplayName("로그인하지 않은 사용자는 친구를 삭제할 수 없다")
    void failWhenUnauthenticatedDeleteFriend() {
        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> friendController.deleteFriend(null, 2L))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("UNAUTHORIZED")
                );

        verify(friendService, never()).deleteFriend(anyLong(), anyLong());
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
