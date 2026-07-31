package jp.co.translacat.domain.chat.openchat.profile.service;

import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatMessageSenderResponseDto;
import jp.co.translacat.domain.chat.openchat.profile.entity.OpenChatMemberProfile;
import jp.co.translacat.domain.chat.openchat.profile.repository.OpenChatMemberProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OpenChatMessageProfileService {

    private final OpenChatMemberProfileRepository profileRepository;
    private final OpenChatProfileImageUrlResolver imageUrlResolver;

    public OpenChatMessageSenderResponseDto resolve(
            Long roomId,
            Long userId
    ) {
        if (roomId == null || userId == null) {
            return null;
        }
        return profileRepository
                .findByChatRoomMemberChatRoomIdAndChatRoomMemberUserId(
                        roomId,
                        userId
                )
                .map(this::toSender)
                .orElse(null);
    }

    public Map<Long, OpenChatMessageSenderResponseDto> resolveMap(
            Long roomId,
            Collection<Long> userIds
    ) {
        if (roomId == null || userIds == null || userIds.isEmpty()) {
            return Map.of();
        }

        Set<Long> normalizedUserIds = userIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (normalizedUserIds.isEmpty()) {
            return Map.of();
        }

        return profileRepository
                .findByChatRoomMemberChatRoomIdAndChatRoomMemberUserIdIn(
                        roomId,
                        normalizedUserIds
                )
                .stream()
                .collect(Collectors.toUnmodifiableMap(
                        profile -> profile.getChatRoomMember()
                                .getUser()
                                .getId(),
                        this::toSender,
                        (left, right) -> left
                ));
    }

    private OpenChatMessageSenderResponseDto toSender(
            OpenChatMemberProfile profile
    ) {
        return new OpenChatMessageSenderResponseDto(
                profile.getChatRoomMember().getId(),
                profile.getMemberCode(),
                profile.getNickname(),
                imageUrlResolver.resolve(
                        profile.getProfileImageObjectKey()
                ),
                profile.getChatRoomMember().getRole()
        );
    }
}
