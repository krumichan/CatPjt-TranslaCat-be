package jp.co.translacat.domain.user.profile.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.profile.dto.UserProfileResponseDto;
import jp.co.translacat.domain.user.profile.dto.UserProfileUpdateRequestDto;
import jp.co.translacat.domain.user.profile.service.UserProfileService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserProfileControllerTest {

    private UserProfileService userProfileService;
    private UserProfileController userProfileController;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        userProfileService = mock(UserProfileService.class);
        userProfileController = new UserProfileController(userProfileService);
        objectMapper = new ObjectMapper();

        mockMvc = MockMvcBuilders
                .standaloneSetup(userProfileController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("내 프로필 조회 API 테스트")
    void getMyProfile() throws Exception {
        Long loginUserId = 1L;
        setAuthentication(loginUserId);

        UserProfileResponseDto response = new UserProfileResponseDto(
                loginUserId,
                "TCAT-00000001",
                "testUser",
                null,
                "안녕하세요",
                LocalDateTime.of(2026, 6, 28, 10, 0),
                LocalDateTime.of(2026, 6, 28, 10, 0)
        );

        when(userProfileService.getMyProfile(loginUserId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/users/me/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value(200))
                .andExpect(jsonPath("$.body.userId").value(loginUserId))
                .andExpect(jsonPath("$.body.publicId").value("TCAT-00000001"))
                .andExpect(jsonPath("$.body.nickname").value("testUser"))
                .andExpect(jsonPath("$.body.bio").value("안녕하세요"));

        verify(userProfileService).getMyProfile(loginUserId);
    }

    @Test
    @DisplayName("내 프로필 수정 API 테스트")
    void updateMyProfile() throws Exception {
        Long loginUserId = 1L;
        setAuthentication(loginUserId);

        UserProfileUpdateRequestDto request = new UserProfileUpdateRequestDto(
                "updatedNickname",
                "https://example.com/profile.png",
                "수정된 자기소개"
        );

        UserProfileResponseDto response = new UserProfileResponseDto(
                loginUserId,
                "TCAT-00000001",
                "updatedNickname",
                "https://example.com/profile.png",
                "수정된 자기소개",
                LocalDateTime.of(2026, 6, 28, 10, 0),
                LocalDateTime.of(2026, 6, 28, 10, 5)
        );

        when(userProfileService.updateMyProfile(eq(loginUserId), any(UserProfileUpdateRequestDto.class)))
                .thenReturn(response);

        mockMvc.perform(
                        patch("/api/v1/users/me/profile")
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value(200))
                .andExpect(jsonPath("$.body.userId").value(loginUserId))
                .andExpect(jsonPath("$.body.publicId").value("TCAT-00000001"))
                .andExpect(jsonPath("$.body.nickname").value("updatedNickname"))
                .andExpect(jsonPath("$.body.profileImageUrl").value("https://example.com/profile.png"))
                .andExpect(jsonPath("$.body.bio").value("수정된 자기소개"));

        verify(userProfileService).updateMyProfile(eq(loginUserId), any(UserProfileUpdateRequestDto.class));
    }

    @Test
    @DisplayName("로그인하지 않은 사용자는 내 프로필을 조회할 수 없다")
    void failToGetMyProfileWhenUnauthenticated() {
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> userProfileController.getMyProfile(null))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("UNAUTHORIZED")
                );

        verify(userProfileService, never()).getMyProfile(anyLong());
    }

    @Test
    @DisplayName("로그인하지 않은 사용자는 내 프로필을 수정할 수 없다")
    void failToUpdateMyProfileWhenUnauthenticated() {
        UserProfileUpdateRequestDto request = new UserProfileUpdateRequestDto(
                "updatedNickname",
                null,
                null
        );

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> userProfileController.updateMyProfile(null, request))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("UNAUTHORIZED")
                );

        verify(userProfileService, never()).updateMyProfile(anyLong(), any(UserProfileUpdateRequestDto.class));
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
