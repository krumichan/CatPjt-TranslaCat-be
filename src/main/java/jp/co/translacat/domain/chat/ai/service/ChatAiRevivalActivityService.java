package jp.co.translacat.domain.chat.ai.service;

import jp.co.translacat.domain.chat.ai.entity.ChatAiSystemSetting;
import jp.co.translacat.domain.chat.ai.entity.ChatRoomAiActivity;
import jp.co.translacat.domain.chat.ai.event.ChatAiHumanMessageRecordedEvent;
import jp.co.translacat.domain.chat.ai.repository.ChatRoomAiActivityRepository;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;
import jp.co.translacat.domain.chat.room.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ChatAiRevivalActivityService {

    private final ChatRoomAiActivityRepository activityRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatAiSystemSettingService systemSettingService;
    private final ChatAiRevivalScheduleCalculator scheduleCalculator;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resetForHumanMessage(
            ChatAiHumanMessageRecordedEvent event
    ) {
        if (event == null
                || event.messageId() == null
                || event.roomId() == null
                || event.createdAt() == null) {
            return;
        }

        ChatRoom room = chatRoomRepository
                .findActiveByIdForUpdate(event.roomId())
                .orElse(null);
        if (!isRevivalRoom(room)) {
            return;
        }

        ChatAiSystemSetting setting = systemSettingService.getOrCreateEntity();
        ChatRoomAiActivity activity = activityRepository
                .findByChatRoomIdForUpdate(event.roomId())
                .orElse(null);

        long nextCycleVersion = activity == null
                ? 1L
                : activity.getRevivalCycleVersion() + 1L;
        LocalDateTime nextRevivalAt = scheduleCalculator.scheduleAfterHours(
                room.getId(),
                nextCycleVersion,
                1,
                event.createdAt(),
                setting.getRevivalFirstDelayHours(),
                setting.getRevivalAllowedStartTime(),
                setting.getRevivalAllowedEndTime()
        );

        if (activity == null) {
            activityRepository.save(
                    ChatRoomAiActivity.create(
                            room,
                            event.messageId(),
                            event.createdAt(),
                            nextRevivalAt
                    )
            );
            return;
        }

        activity.resetForHumanMessage(
                event.messageId(),
                event.createdAt(),
                nextRevivalAt
        );
    }

    private boolean isRevivalRoom(ChatRoom room) {
        return room != null
                && (room.getRoomType() == ChatRoomType.GROUP
                || room.getRoomType() == ChatRoomType.OPEN);
    }
}
