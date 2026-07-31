package jp.co.translacat.domain.chat.openchat.profile.service;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatMemberProfileResponseDto;
import jp.co.translacat.domain.chat.openchat.profile.entity.OpenChatMemberProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OpenChatProfileResponseMapper {

    private final OpenChatProfileImageUrlResolver imageUrlResolver;

    public OpenChatMemberProfileResponseDto toResponse(
            OpenChatMemberProfile profile
    ) {
        ChatRoomMember member = profile.getChatRoomMember();
        return new OpenChatMemberProfileResponseDto(
                member.getId(),
                profile.getMemberCode(),
                profile.getNickname(),
                imageUrlResolver.resolve(
                        profile.getProfileImageObjectKey()
                ),
                member.getRole(),
                member.isActive(),
                member.getJoinedAt()
        );
    }
}
