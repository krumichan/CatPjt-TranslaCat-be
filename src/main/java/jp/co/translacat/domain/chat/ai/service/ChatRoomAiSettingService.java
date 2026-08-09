package jp.co.translacat.domain.chat.ai.service;

import jp.co.translacat.domain.chat.ai.dto.request.ChatRoomAiSettingUpdateRequestDto;
import jp.co.translacat.domain.chat.ai.dto.response.ChatRoomAiSettingResponseDto;
import jp.co.translacat.domain.chat.ai.entity.ChatAiSystemSetting;
import jp.co.translacat.domain.chat.ai.entity.ChatRoomAiSetting;
import jp.co.translacat.domain.chat.ai.enums.ChatAiDisclosureType;
import jp.co.translacat.domain.chat.ai.repository.ChatRoomAiMemberRepository;
import jp.co.translacat.domain.chat.ai.repository.ChatRoomAiSettingRepository;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.ai.support.ChatAiErrorCode;
import jp.co.translacat.domain.chat.member.event.ChatRoomMembersChangedApplicationEvent;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomAiSettingService {

    private final ChatAiAccessService accessService;
    private final ChatRoomAiSettingRepository settingRepository;
    private final ChatRoomAiMemberRepository aiMemberRepository;
    private final ChatAiSystemSettingService systemSettingService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ChatRoomAiSettingResponseDto getSettings(
            Long loginUserId,
            Long roomId
    ) {
        ChatRoom room = accessService.getAccessibleRoom(
                loginUserId,
                roomId
        );
        ChatRoomAiSetting setting = getOrCreate(room);
        return toResponse(setting);
    }

    @Transactional
    public ChatRoomAiSettingResponseDto updateSettings(
            Long loginUserId,
            Long roomId,
            ChatRoomAiSettingUpdateRequestDto request
    ) {
        if (request == null) {
            throw new BusinessException(
                    "채팅방 AI 설정 요청은 필수입니다.",
                    ChatAiErrorCode.SETTING_INVALID
            );
        }
        ChatRoom room = accessService.getManageableRoomForUpdate(
                loginUserId,
                roomId
        );
        ChatRoomAiSetting setting = getOrCreate(room);
        ChatAiDisclosureType previousDisclosureType =
                setting.getDisclosureType();
        setting.update(
                request.disclosureType(),
                request.mentionPermission(),
                request.conversationEnabled(),
                request.revivalEnabled()
        );
        if (previousDisclosureType != setting.getDisclosureType()) {
            eventPublisher.publishEvent(
                    ChatRoomMembersChangedApplicationEvent.of(roomId)
            );
        }
        return toResponse(setting);
    }

    @Transactional
    public ChatRoomAiSetting getOrCreate(ChatRoom room) {
        return settingRepository.findByChatRoomId(room.getId())
                .orElseGet(() -> settingRepository.save(
                        ChatRoomAiSetting.createDefault(room)
                ));
    }

    private ChatRoomAiSettingResponseDto toResponse(
            ChatRoomAiSetting setting
    ) {
        long currentCount = aiMemberRepository
                .countByChatRoomIdAndActiveTrueAndDeletedAtIsNull(
                        setting.getChatRoom().getId()
                );
        ChatAiSystemSetting systemSetting =
                systemSettingService.getOrCreateEntity();
        return ChatRoomAiSettingResponseDto.from(
                setting,
                currentCount,
                systemSetting.getMaxAiMembersPerRoom()
        );
    }
}
