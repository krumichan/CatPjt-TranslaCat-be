package jp.co.translacat.domain.user.block.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.user.block.dto.UserBlockRequestDto;
import jp.co.translacat.domain.user.block.dto.UserBlockResponseDto;
import jp.co.translacat.domain.user.block.service.UserBlockService;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.profile.dto.UserSummaryProfileResponseDto;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserBlockControllerTest {

    private UserBlockService userBlockService;
    private UserBlockController userBlockController;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        userBlockService = mock(UserBlockService.class);
        userBlockController = new UserBlockController(userBlockService);
        objectMapper = new ObjectMapper();

        mockMvc = MockMvcBuilders
                .standaloneSetup(userBlockController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("사용자 차단 API 테스트")
    void blockUser() throws Exception {
        // given
        Long loginUserId = 1L;
        setAuthentication(loginUserId);

        UserBlockRequestDto request = new UserBlockRequestDto("TCAT-00000002");
        UserBlockResponseDto response = createResponse();

        when(userBlockService.blockUser(loginUserId, request)).thenReturn(response);

        // when & then
        mockMvc.perform(post("/api/v1/blocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value(200))
                .andExpect(jsonPath("$.body.blockedUser.userId").value(2L))
                .andExpect(jsonPath("$.body.blockedUser.publicId").value("TCAT-00000002"));

        verify(userBlockService).blockUser(loginUserId, request);
    }

    @Test
    @DisplayName("차단 목록 조회 API 테스트")
    void getBlockedUsers() throws Exception {
        // given
        Long loginUserId = 1L;
        setAuthentication(loginUserId);

        when(userBlockService.getBlockedUsers(loginUserId)).thenReturn(List.of(createResponse()));

        // when & then
        mockMvc.perform(get("/api/v1/blocks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value(200))
                .andExpect(jsonPath("$.body[0].blockedUser.userId").value(2L))
                .andExpect(jsonPath("$.body[0].blockedUser.publicId").value("TCAT-00000002"));

        verify(userBlockService).getBlockedUsers(loginUserId);
    }

    @Test
    @DisplayName("차단 해제 API 테스트")
    void unblockUser() throws Exception {
        // given
        Long loginUserId = 1L;
        Long blockedUserId = 2L;
        setAuthentication(loginUserId);

        // when & then
        mockMvc.perform(delete("/api/v1/blocks/{blockedUserId}", blockedUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value(200))
                .andExpect(jsonPath("$.body").value(true));

        verify(userBlockService).unblockUser(loginUserId, blockedUserId);
    }

    @Test
    @DisplayName("로그인하지 않은 사용자는 차단할 수 없다")
    void failWhenUnauthenticatedBlockUser() {
        // given
        UserBlockRequestDto request = new UserBlockRequestDto("TCAT-00000002");

        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> userBlockController.blockUser(null, request))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("UNAUTHORIZED")
                );

        verify(userBlockService, never()).blockUser(anyLong(), any(UserBlockRequestDto.class));
    }

    @Test
    @DisplayName("로그인하지 않은 사용자는 차단 목록을 조회할 수 없다")
    void failWhenUnauthenticatedGetBlockedUsers() {
        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> userBlockController.getBlockedUsers(null))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("UNAUTHORIZED")
                );

        verify(userBlockService, never()).getBlockedUsers(anyLong());
    }

    @Test
    @DisplayName("로그인하지 않은 사용자는 차단 해제할 수 없다")
    void failWhenUnauthenticatedUnblockUser() {
        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> userBlockController.unblockUser(null, 2L))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("UNAUTHORIZED")
                );

        verify(userBlockService, never()).unblockUser(anyLong(), anyLong());
    }

    private UserBlockResponseDto createResponse() {
        return new UserBlockResponseDto(
                100L,
                new UserSummaryProfileResponseDto(
                        2L,
                        "TCAT-00000002",
                        "blockedUser",
                        null
                ),
                LocalDateTime.of(2026, 6, 28, 10, 0)
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
