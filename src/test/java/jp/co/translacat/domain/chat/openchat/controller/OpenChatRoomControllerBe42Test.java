package jp.co.translacat.domain.chat.openchat.controller;

import jp.co.translacat.domain.chat.openchat.dto.request.OpenChatJoinRequestDto;
import jp.co.translacat.domain.chat.openchat.dto.request.OpenChatOwnerTransferRequestDto;
import jp.co.translacat.domain.chat.openchat.dto.request.OpenChatProfileRequestDto;
import jp.co.translacat.domain.chat.openchat.dto.request.OpenChatProfileUpdateRequestDto;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatMemberListResponseDto;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatMemberProfileResponseDto;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatMembershipResponseDto;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatRoomDetailResponseDto;
import jp.co.translacat.domain.chat.openchat.profile.service.OpenChatProfileService;
import jp.co.translacat.domain.chat.openchat.service.OpenChatMembershipService;
import jp.co.translacat.domain.chat.openchat.service.OpenChatRoomCommandService;
import jp.co.translacat.domain.chat.openchat.service.OpenChatRoomQueryService;
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
class OpenChatRoomControllerBe42Test {

    @Mock private OpenChatRoomCommandService commandService;
    @Mock private OpenChatRoomQueryService queryService;
    @Mock private OpenChatMembershipService membershipService;
    @Mock private OpenChatProfileService profileService;
    @Mock private UserPrincipal userPrincipal;

    private OpenChatRoomController controller;

    @BeforeEach
    void setUp() {
        controller = new OpenChatRoomController(
                commandService,
                queryService,
                membershipService,
                profileService
        );
        when(userPrincipal.getId()).thenReturn(10L);
    }

    @Test
    void delegatesJoinWithAuthenticatedUser() {
        OpenChatJoinRequestDto request = new OpenChatJoinRequestDto(
                new OpenChatProfileRequestDto("cat", null)
        );

        OpenChatRoomDetailResponseDto response =
                mock(OpenChatRoomDetailResponseDto.class);
        when(membershipService.join(10L, 100L, request))
                .thenReturn(response);

        controller.join(userPrincipal, 100L, request);

        verify(membershipService).join(10L, 100L, request);
    }

    @Test
    void delegatesLeaveAndOwnerLifecycle() {
        OpenChatOwnerTransferRequestDto request =
                new OpenChatOwnerTransferRequestDto(20L);

        OpenChatMembershipResponseDto leaveResponse =
                mock(OpenChatMembershipResponseDto.class);
        OpenChatRoomDetailResponseDto transferResponse =
                mock(OpenChatRoomDetailResponseDto.class);
        OpenChatRoomDetailResponseDto closeResponse =
                mock(OpenChatRoomDetailResponseDto.class);

        when(membershipService.leave(10L, 100L))
                .thenReturn(leaveResponse);
        when(membershipService.transferOwner(10L, 100L, request))
                .thenReturn(transferResponse);
        when(membershipService.close(10L, 100L))
                .thenReturn(closeResponse);

        controller.leave(userPrincipal, 100L);
        controller.transferOwner(userPrincipal, 100L, request);
        controller.close(userPrincipal, 100L);

        verify(membershipService).leave(10L, 100L);
        verify(membershipService).transferOwner(10L, 100L, request);
        verify(membershipService).close(10L, 100L);
    }

    @Test
    void delegatesRoomScopedProfileApis() {
        OpenChatProfileUpdateRequestDto request =
                new OpenChatProfileUpdateRequestDto("new-cat");

        OpenChatMemberProfileResponseDto myProfileResponse =
                mock(OpenChatMemberProfileResponseDto.class);
        OpenChatMemberProfileResponseDto updatedProfileResponse =
                mock(OpenChatMemberProfileResponseDto.class);
        OpenChatMemberListResponseDto memberListResponse =
                mock(OpenChatMemberListResponseDto.class);
        OpenChatMemberProfileResponseDto memberProfileResponse =
                mock(OpenChatMemberProfileResponseDto.class);

        when(profileService.getMyProfile(10L, 100L))
                .thenReturn(myProfileResponse);
        when(profileService.updateMyProfile(10L, 100L, request))
                .thenReturn(updatedProfileResponse);
        when(profileService.getMembers(10L, 100L))
                .thenReturn(memberListResponse);
        when(profileService.getMemberProfile(10L, 100L, 20L))
                .thenReturn(memberProfileResponse);

        controller.getMyProfile(userPrincipal, 100L);
        controller.updateMyProfile(userPrincipal, 100L, request);
        controller.getMembers(userPrincipal, 100L);
        controller.getMemberProfile(userPrincipal, 100L, 20L);

        verify(profileService).getMyProfile(10L, 100L);
        verify(profileService).updateMyProfile(10L, 100L, request);
        verify(profileService).getMembers(10L, 100L);
        verify(profileService).getMemberProfile(10L, 100L, 20L);
    }
}
