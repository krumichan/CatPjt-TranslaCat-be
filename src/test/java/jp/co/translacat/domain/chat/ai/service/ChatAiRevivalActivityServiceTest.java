package jp.co.translacat.domain.chat.ai.service;

import jp.co.translacat.domain.chat.ai.entity.ChatAiSystemSetting;
import jp.co.translacat.domain.chat.ai.entity.ChatRoomAiActivity;
import jp.co.translacat.domain.chat.ai.event.ChatAiHumanMessageRecordedEvent;
import jp.co.translacat.domain.chat.ai.repository.ChatRoomAiActivityRepository;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.repository.ChatRoomRepository;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatAiRevivalActivityServiceTest {

    @Mock private ChatRoomAiActivityRepository activityRepository;
    @Mock private ChatRoomRepository roomRepository;
    @Mock private ChatAiSystemSettingService systemSettingService;

    private ChatAiRevivalActivityService service;
    private ChatAiRevivalScheduleCalculator calculator;
    private ChatRoom room;

    @BeforeEach
    void setUp() {
        calculator = new ChatAiRevivalScheduleCalculator("Asia/Tokyo");
        service = new ChatAiRevivalActivityService(
                activityRepository,
                roomRepository,
                systemSettingService,
                calculator
        );
        User owner = User.createLocalUser(
                "revival-activity@test.com",
                "password",
                "owner",
                Role.USER,
                "REVACT001"
        );
        room = ChatRoom.createGroupRoom("group", "desc", owner);
        ReflectionTestUtils.setField(room, "id", 100L);
    }

    @Test
    void humanMessageCreatesActivityWithFirstRevivalSchedule() {
        LocalDateTime humanAt = LocalDateTime.of(2026, 8, 9, 9, 0);
        ChatAiSystemSetting setting = ChatAiSystemSetting.createDefault();
        when(roomRepository.findActiveByIdForUpdate(100L))
                .thenReturn(Optional.of(room));
        when(systemSettingService.getOrCreateEntity()).thenReturn(setting);
        when(activityRepository.findByChatRoomIdForUpdate(100L))
                .thenReturn(Optional.empty());

        service.resetForHumanMessage(new ChatAiHumanMessageRecordedEvent(
                500L,
                100L,
                humanAt
        ));

        ArgumentCaptor<ChatRoomAiActivity> captor =
                ArgumentCaptor.forClass(ChatRoomAiActivity.class);
        verify(activityRepository).save(captor.capture());
        ChatRoomAiActivity saved = captor.getValue();
        assertThat(saved.getLastHumanMessageId()).isEqualTo(500L);
        assertThat(saved.getRevivalStage()).isZero();
        assertThat(saved.getNextRevivalAt())
                .isAfterOrEqualTo(humanAt.plusHours(24));
    }
}
