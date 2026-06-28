package jp.co.translacat.domain.user.block.service;

import jp.co.translacat.domain.user.block.dto.UserBlockRequestDto;
import jp.co.translacat.domain.user.block.dto.UserBlockResponseDto;
import jp.co.translacat.domain.user.block.entity.UserBlock;
import jp.co.translacat.domain.user.block.repository.UserBlockRepository;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.friend.service.FriendService;
import jp.co.translacat.domain.user.profile.dto.UserSummaryProfileResponseDto;
import jp.co.translacat.domain.user.profile.service.UserProfileService;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserBlockApiServiceTest {

    @Mock
    private UserBlockRepository userBlockRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FriendService friendService;

    @Mock
    private UserProfileService userProfileService;

    @InjectMocks
    private UserBlockService userBlockService;

    @Test
    @DisplayName("publicId 기반 사용자 차단 API 서비스 테스트")
    void blockUserByPublicId() {
        // given
        User blocker = createUser(1L, "blocker@example.com", "blocker", "TCAT-00000001");
        User blocked = createUser(2L, "blocked@example.com", "blocked", "TCAT-00000002");
        UserBlockRequestDto request = new UserBlockRequestDto("TCAT-00000002");

        when(userRepository.findByPublicId("TCAT-00000002")).thenReturn(Optional.of(blocked));
        when(userRepository.findById(1L)).thenReturn(Optional.of(blocker));
        when(userRepository.findById(2L)).thenReturn(Optional.of(blocked));
        when(userBlockRepository.findByBlockerAndBlocked(1L, 2L)).thenReturn(Optional.empty());
        when(userBlockRepository.save(any(UserBlock.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(friendService.areFriends(1L, 2L)).thenReturn(false);
        when(userProfileService.getSummaryByUser(blocked)).thenReturn(createSummary(blocked));

        // when
        UserBlockResponseDto response = userBlockService.blockUser(1L, request);

        // then
        assertThat(response.blockedUser().userId()).isEqualTo(2L);
        assertThat(response.blockedUser().publicId()).isEqualTo("TCAT-00000002");

        verify(userBlockRepository).save(any(UserBlock.class));
        verify(friendService, never()).deleteFriend(anyLong(), anyLong());
    }

    @Test
    @DisplayName("차단 목록 조회 API 서비스 테스트")
    void getBlockedUsers() {
        // given
        User blocker = createUser(1L, "blocker@example.com", "blocker", "TCAT-00000001");
        User blocked = createUser(2L, "blocked@example.com", "blocked", "TCAT-00000002");
        UserBlock userBlock = UserBlock.create(blocker, blocked);

        when(userBlockRepository.findActiveBlocksByBlockerUserId(1L)).thenReturn(List.of(userBlock));
        when(userProfileService.getSummaryByUser(blocked)).thenReturn(createSummary(blocked));

        // when
        List<UserBlockResponseDto> responses = userBlockService.getBlockedUsers(1L);

        // then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).blockedUser().userId()).isEqualTo(2L);
        assertThat(responses.get(0).blockedUser().publicId()).isEqualTo("TCAT-00000002");
    }

    @Test
    @DisplayName("차단 해제 API 서비스 테스트")
    void unblockUser() {
        // given
        User blocker = createUser(1L, "blocker@example.com", "blocker", "TCAT-00000001");
        User blocked = createUser(2L, "blocked@example.com", "blocked", "TCAT-00000002");
        UserBlock userBlock = UserBlock.create(blocker, blocked);

        when(userBlockRepository.findActiveByBlockerAndBlocked(1L, 2L)).thenReturn(Optional.of(userBlock));

        // when
        userBlockService.unblockUser(1L, 2L);

        // then
        assertThat(userBlock.isDeleted()).isTrue();
        assertThat(userBlock.isActive()).isFalse();
        assertThat(userBlock.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("차단 대상 publicId가 비어 있으면 실패한다")
    void failWhenBlockedPublicIdIsBlank() {
        // given
        UserBlockRequestDto request = new UserBlockRequestDto(" ");

        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> userBlockService.blockUser(1L, request))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("BLOCKED_PUBLIC_ID_REQUIRED")
                );
    }

    @Test
    @DisplayName("존재하지 않는 publicId는 차단할 수 없다")
    void failWhenBlockedUserDoesNotExist() {
        // given
        UserBlockRequestDto request = new UserBlockRequestDto("TCAT-NOTFOUND");

        when(userRepository.findByPublicId("TCAT-NOTFOUND")).thenReturn(Optional.empty());

        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> userBlockService.blockUser(1L, request))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("PUBLIC_ID_NOT_FOUND")
                );
    }

    private UserSummaryProfileResponseDto createSummary(User user) {
        return new UserSummaryProfileResponseDto(
                user.getId(),
                user.getPublicId(),
                user.getUsername(),
                null
        );
    }

    private User createUser(
            Long id,
            String email,
            String username,
            String publicId
    ) {
        User user = User.createLocalUser(
                email,
                "password",
                username,
                Role.USER,
                publicId
        );
        user.setId(id);
        return user;
    }
}
