package jp.co.translacat.domain.chat.ai.service;

import jp.co.translacat.domain.chat.ai.dto.response.ChatAiDisplayMemberResponseDto;
import jp.co.translacat.domain.chat.ai.dto.response.ChatAiDisplayMembersResponseDto;
import jp.co.translacat.domain.chat.ai.dto.response.ChatAiSafeProfileResponseDto;
import jp.co.translacat.domain.chat.ai.entity.ChatRoomAiMember;
import jp.co.translacat.domain.chat.ai.enums.ChatAiDisclosureType;
import jp.co.translacat.domain.chat.ai.repository.ChatRoomAiMemberRepository;
import jp.co.translacat.domain.chat.ai.repository.ChatRoomAiSettingRepository;
import jp.co.translacat.domain.chat.ai.support.ChatAiErrorCode;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatAiDisplayMemberService {

    private final ChatAiAccessService accessService;
    private final ChatRoomAiMemberRepository aiMemberRepository;
    private final ChatRoomAiSettingRepository aiSettingRepository;
    private final ChatAiProfileImageUrlResolver imageUrlResolver;

    public ChatAiDisplayMembersResponseDto getDisplayMembers(Long roomId) {
        List<ChatAiDisplayMemberResponseDto> members = aiMemberRepository
                .findByChatRoomIdAndActiveTrueAndDeletedAtIsNullOrderByJoinedAtAsc(roomId)
                .stream()
                .filter(member -> member.getAiAgent().isActive())
                .filter(member -> member.getAiAgent().getDeletedAt() == null)
                .map(member -> ChatAiDisplayMemberResponseDto.from(
                        member,
                        imageUrlResolver.resolveProfileImageUrl(member.getAiAgent())
                ))
                .toList();

        if (members.isEmpty()) {
            return ChatAiDisplayMembersResponseDto.empty();
        }

        ChatAiDisclosureType disclosureType = aiSettingRepository
                .findByChatRoomId(roomId)
                .map(setting -> setting.getDisclosureType())
                .orElse(ChatAiDisclosureType.PUBLIC);

        return new ChatAiDisplayMembersResponseDto(
                disclosureType,
                members
        );
    }

    public ChatAiSafeProfileResponseDto getSafeProfile(
            Long loginUserId,
            Long roomId,
            Long aiMemberId
    ) {
        accessService.getAccessibleRoom(loginUserId, roomId);
        ChatRoomAiMember aiMember = aiMemberRepository
                .findByIdAndChatRoomIdAndActiveTrueAndDeletedAtIsNull(
                        aiMemberId,
                        roomId
                )
                .orElseThrow(() -> memberNotFound());
        if (!aiMember.getAiAgent().isActive()
                || aiMember.getAiAgent().getDeletedAt() != null) {
            throw memberNotFound();
        }

        return ChatAiSafeProfileResponseDto.from(
                aiMember,
                imageUrlResolver.resolveProfileImageUrl(aiMember.getAiAgent()),
                imageUrlResolver.resolveProfileBackgroundImageUrl(aiMember.getAiAgent())
        );
    }

    private BusinessException memberNotFound() {
        return new BusinessException(
                "활성 AI 멤버를 찾을 수 없습니다.",
                ChatAiErrorCode.MEMBER_NOT_FOUND
        );
    }
}
