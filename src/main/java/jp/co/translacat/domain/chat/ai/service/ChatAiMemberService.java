package jp.co.translacat.domain.chat.ai.service;

import jp.co.translacat.domain.chat.ai.dto.request.ChatAiMemberCreateRequestDto;
import jp.co.translacat.domain.chat.ai.dto.request.ChatAiMemberUpdateRequestDto;
import jp.co.translacat.domain.chat.ai.dto.response.ChatAiMemberListResponseDto;
import jp.co.translacat.domain.chat.ai.dto.response.ChatAiMemberResponseDto;
import jp.co.translacat.domain.chat.ai.entity.ChatAiAgent;
import jp.co.translacat.domain.chat.ai.entity.ChatAiSystemSetting;
import jp.co.translacat.domain.chat.ai.entity.ChatRoomAiMember;
import jp.co.translacat.domain.chat.ai.repository.ChatAiAgentRepository;
import jp.co.translacat.domain.chat.ai.repository.ChatRoomAiMemberRepository;
import jp.co.translacat.domain.chat.ai.support.ChatAiErrorCode;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatAiMemberService {

    private final ChatAiAccessService accessService;
    private final ChatAiAgentRepository agentRepository;
    private final ChatRoomAiMemberRepository aiMemberRepository;
    private final ChatRoomAiSettingService roomSettingService;
    private final ChatAiSystemSettingService systemSettingService;
    private final ChatAiProfileImageUrlResolver imageUrlResolver;

    @Transactional
    public ChatAiMemberListResponseDto getMembers(
            Long loginUserId,
            Long roomId
    ) {
        accessService.getManageableRoom(loginUserId, roomId);
        List<ChatAiMemberResponseDto> members = aiMemberRepository
                .findByChatRoomIdAndActiveTrueAndDeletedAtIsNullOrderByJoinedAtAsc(roomId)
                .stream()
                .map(this::toResponse)
                .toList();
        int maxCount = systemSettingService
                .getOrCreateEntity()
                .getMaxAiMembersPerRoom();
        return new ChatAiMemberListResponseDto(
                roomId,
                members.size(),
                maxCount,
                members
        );
    }

    public ChatAiMemberResponseDto getMember(
            Long loginUserId,
            Long roomId,
            Long aiMemberId
    ) {
        accessService.getManageableRoom(loginUserId, roomId);
        return toResponse(getActiveMember(roomId, aiMemberId));
    }

    @Transactional
    public ChatAiMemberResponseDto create(
            Long loginUserId,
            Long roomId,
            ChatAiMemberCreateRequestDto request
    ) {
        if (request == null) {
            throw new BusinessException(
                    "AI 멤버 생성 요청은 필수입니다.",
                    ChatAiErrorCode.REQUEST_REQUIRED
            );
        }

        ChatRoom room = accessService.getManageableRoomForUpdate(
                loginUserId,
                roomId
        );
        ChatAiSystemSetting systemSetting =
                systemSettingService.getOrCreateEntity();
        long currentCount = aiMemberRepository
                .countByChatRoomIdAndActiveTrueAndDeletedAtIsNull(roomId);
        if (currentCount >= systemSetting.getMaxAiMembersPerRoom()) {
            throw new BusinessException(
                    "채팅방 AI 멤버 최대 인원을 초과했습니다.",
                    ChatAiErrorCode.MAX_MEMBER_COUNT_EXCEEDED
            );
        }

        ChatAiAgent agent = agentRepository.save(
                ChatAiAgent.create(
                        request.nickname(),
                        request.bio(),
                        request.originalLanguageCode(),
                        request.personaPrompt()
                )
        );
        ChatRoomAiMember aiMember = aiMemberRepository.save(
                ChatRoomAiMember.create(room, agent)
        );
        roomSettingService.getOrCreate(room);
        return toResponse(aiMember);
    }

    @Transactional
    public ChatAiMemberResponseDto update(
            Long loginUserId,
            Long roomId,
            Long aiMemberId,
            ChatAiMemberUpdateRequestDto request
    ) {
        if (request == null) {
            throw new BusinessException(
                    "AI 멤버 수정 요청은 필수입니다.",
                    ChatAiErrorCode.REQUEST_REQUIRED
            );
        }
        accessService.getManageableRoomForUpdate(loginUserId, roomId);
        ChatRoomAiMember aiMember = getActiveMember(roomId, aiMemberId);
        aiMember.getAiAgent().updateProfile(
                request.nickname(),
                request.bio(),
                request.originalLanguageCode(),
                request.personaPrompt()
        );
        return toResponse(aiMember);
    }

    @Transactional
    public ChatAiMemberResponseDto delete(
            Long loginUserId,
            Long roomId,
            Long aiMemberId
    ) {
        accessService.getManageableRoomForUpdate(loginUserId, roomId);
        ChatRoomAiMember aiMember = getActiveMember(roomId, aiMemberId);
        aiMember.softDelete();
        aiMember.getAiAgent().softDelete();
        return toResponse(aiMember);
    }

    public ChatRoomAiMember getActiveMember(
            Long roomId,
            Long aiMemberId
    ) {
        if (aiMemberId == null || aiMemberId <= 0) {
            throw memberNotFound();
        }
        return aiMemberRepository
                .findByIdAndChatRoomIdAndActiveTrueAndDeletedAtIsNull(
                        aiMemberId,
                        roomId
                )
                .orElseThrow(this::memberNotFound);
    }

    public ChatAiMemberResponseDto toResponse(ChatRoomAiMember aiMember) {
        return ChatAiMemberResponseDto.from(
                aiMember,
                imageUrlResolver.resolveProfileImageUrl(
                        aiMember.getAiAgent()
                ),
                imageUrlResolver.resolveProfileBackgroundImageUrl(
                        aiMember.getAiAgent()
                )
        );
    }

    private BusinessException memberNotFound() {
        return new BusinessException(
                "활성 AI 멤버를 찾을 수 없습니다.",
                ChatAiErrorCode.MEMBER_NOT_FOUND
        );
    }
}
