package jp.co.translacat.domain.chat.ai.service;

import jp.co.translacat.domain.chat.ai.dto.server.ChatAiRevivalClaim;
import jp.co.translacat.domain.chat.ai.entity.ChatAiAgent;
import jp.co.translacat.domain.chat.ai.entity.ChatAiSystemSetting;
import jp.co.translacat.domain.chat.ai.entity.ChatRoomAiActivity;
import jp.co.translacat.domain.chat.ai.entity.ChatRoomAiMember;
import jp.co.translacat.domain.chat.ai.entity.ChatRoomAiSetting;
import jp.co.translacat.domain.chat.ai.enums.ChatAiTriggerProcessingResult;
import jp.co.translacat.domain.chat.ai.repository.ChatRoomAiActivityRepository;
import jp.co.translacat.domain.chat.ai.repository.ChatRoomAiMemberRepository;
import jp.co.translacat.domain.chat.ai.repository.ChatRoomAiSettingRepository;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatAiRevivalClaimServiceTest {

    @Mock private ChatRoomAiActivityRepository activityRepository;
    @Mock private ChatRoomAiSettingRepository roomSettingRepository;
    @Mock private ChatRoomAiMemberRepository aiMemberRepository;
    @Mock private ChatAiSystemSettingService systemSettingService;

    private ChatAiRevivalClaimService service;
    private ChatRoom room;
    private ChatRoomAiActivity activity;
    private ChatRoomAiMember ai1;
    private ChatRoomAiMember ai2;
    private LocalDateTime dueAt;
    private ChatAiSystemSetting systemSetting;

    @BeforeEach
    void setUp() {
        service = new ChatAiRevivalClaimService(
                activityRepository,
                roomSettingRepository,
                aiMemberRepository,
                systemSettingService,
                new ChatAiRevivalScheduleCalculator("Asia/Tokyo")
        );
        ReflectionTestUtils.setField(service, "claimTimeoutSeconds", 120);
        ReflectionTestUtils.setField(service, "failureRetryMinutes", 5);

        User owner = User.createLocalUser(
                "claim-owner@test.com",
                "password",
                "owner",
                Role.USER,
                "CLAIMOWN01"
        );
        room = ChatRoom.createGroupRoom("group", "desc", owner);
        ReflectionTestUtils.setField(room, "id", 100L);
        dueAt = LocalDateTime.of(2026, 8, 10, 12, 0);
        systemSetting = ChatAiSystemSetting.createDefault();
        activity = ChatRoomAiActivity.create(
                room,
                10L,
                dueAt.minusHours(24),
                dueAt
        );
        ReflectionTestUtils.setField(activity, "id", 900L);

        ai1 = aiMember(1L, "Mika");
        ai2 = aiMember(2L, "Coco");
    }

    @Test
    void revivalDisabledRoomCannotBeClaimed() {
        ChatRoomAiSetting roomSetting = ChatRoomAiSetting.createDefault(room);
        roomSetting.update(null, null, null, false);
        when(activityRepository.findByIdForUpdate(900L))
                .thenReturn(Optional.of(activity));
        when(roomSettingRepository.findByChatRoomId(100L))
                .thenReturn(Optional.of(roomSetting));

        assertThat(service.claim(900L, dueAt)).isEmpty();
        verify(aiMemberRepository, never())
                .findByChatRoomIdAndActiveTrueAndDeletedAtIsNullOrderByJoinedAtAsc(100L);
    }

    @Test
    void claimSelectsOnlyOneAiAndUnexpiredClaimBlocksDuplicateExecution() {
        ChatRoomAiSetting roomSetting = ChatRoomAiSetting.createDefault(room);
        when(activityRepository.findByIdForUpdate(900L))
                .thenReturn(Optional.of(activity));
        when(roomSettingRepository.findByChatRoomId(100L))
                .thenReturn(Optional.of(roomSetting));
        when(systemSettingService.getOrCreateEntity())
                .thenReturn(systemSetting);
        when(aiMemberRepository
                .findByChatRoomIdAndActiveTrueAndDeletedAtIsNullOrderByJoinedAtAsc(100L))
                .thenReturn(List.of(ai1, ai2));

        ChatAiRevivalClaim first = service.claim(900L, dueAt).orElseThrow();
        assertThat(first.aiMemberId()).isEqualTo(1L);
        assertThat(first.requestId()).isEqualTo("chat-ai:revival:900:1:1");
        assertThat(service.claim(900L, dueAt.plusSeconds(1))).isEmpty();
    }

    @Test
    void completedAttemptUsesBackoffAndNextAttemptRotatesAi() {
        ChatRoomAiSetting roomSetting = ChatRoomAiSetting.createDefault(room);
        when(activityRepository.findByIdForUpdate(900L))
                .thenReturn(Optional.of(activity));
        when(roomSettingRepository.findByChatRoomId(100L))
                .thenReturn(Optional.of(roomSetting));
        when(aiMemberRepository
                .findByChatRoomIdAndActiveTrueAndDeletedAtIsNullOrderByJoinedAtAsc(100L))
                .thenReturn(List.of(ai1, ai2));
        when(systemSettingService.getOrCreateEntity())
                .thenReturn(systemSetting);

        ChatAiRevivalClaim first = service.claim(900L, dueAt).orElseThrow();
        assertThat(service.finish(
                first,
                ChatAiTriggerProcessingResult.SKIPPED,
                dueAt
        )).isTrue();
        assertThat(activity.getRevivalStage()).isEqualTo(1);
        assertThat(activity.getNextRevivalAt())
                .isAfterOrEqualTo(dueAt.plusHours(72));

        LocalDateTime secondDue = activity.getNextRevivalAt();
        ChatAiRevivalClaim second = service.claim(
                900L,
                secondDue
        ).orElseThrow();
        assertThat(second.aiMemberId()).isEqualTo(2L);
        assertThat(second.attemptNumber()).isEqualTo(2);
        assertThat(second.requestId()).isEqualTo("chat-ai:revival:900:1:2");
    }

    @Test
    void dueOutsideAllowedWindowIsPostponedWithoutAiCall() {
        ChatRoomAiSetting roomSetting = ChatRoomAiSetting.createDefault(room);
        LocalDateTime outsideWindow = LocalDateTime.of(2026, 8, 10, 23, 0);
        ReflectionTestUtils.setField(
                activity,
                "nextRevivalAt",
                outsideWindow.minusMinutes(1)
        );
        when(activityRepository.findByIdForUpdate(900L))
                .thenReturn(Optional.of(activity));
        when(roomSettingRepository.findByChatRoomId(100L))
                .thenReturn(Optional.of(roomSetting));
        when(systemSettingService.getOrCreateEntity())
                .thenReturn(systemSetting);

        assertThat(service.claim(900L, outsideWindow)).isEmpty();
        assertThat(activity.getNextRevivalAt().toLocalDate())
                .isEqualTo(outsideWindow.toLocalDate().plusDays(1));
        assertThat(activity.getNextRevivalAt().toLocalTime())
                .isAfterOrEqualTo(java.time.LocalTime.of(10, 0));
        assertThat(activity.getNextRevivalAt().toLocalTime())
                .isBefore(java.time.LocalTime.of(22, 0));
        verify(aiMemberRepository, never())
                .findByChatRoomIdAndActiveTrueAndDeletedAtIsNullOrderByJoinedAtAsc(100L);
    }

    @Test
    void transientFailureRetriesSameStageAndSameIdempotencyKey() {
        ChatRoomAiSetting roomSetting = ChatRoomAiSetting.createDefault(room);
        when(activityRepository.findByIdForUpdate(900L))
                .thenReturn(Optional.of(activity));
        when(roomSettingRepository.findByChatRoomId(100L))
                .thenReturn(Optional.of(roomSetting));
        when(systemSettingService.getOrCreateEntity())
                .thenReturn(systemSetting);
        when(aiMemberRepository
                .findByChatRoomIdAndActiveTrueAndDeletedAtIsNullOrderByJoinedAtAsc(100L))
                .thenReturn(List.of(ai1, ai2));

        ChatAiRevivalClaim first = service.claim(900L, dueAt).orElseThrow();
        assertThat(service.finish(
                first,
                ChatAiTriggerProcessingResult.FAILED,
                dueAt
        )).isTrue();

        assertThat(activity.getRevivalStage()).isZero();
        LocalDateTime retryAt = activity.getNextRevivalAt();
        ChatAiRevivalClaim retried = service.claim(900L, retryAt).orElseThrow();
        assertThat(retried.attemptNumber()).isEqualTo(1);
        assertThat(retried.aiMemberId()).isEqualTo(1L);
        assertThat(retried.requestId()).isEqualTo(first.requestId());
    }

    private ChatRoomAiMember aiMember(Long id, String nickname) {
        ChatAiAgent agent = ChatAiAgent.create(
                nickname,
                "bio",
                "ja",
                "persona"
        );
        ReflectionTestUtils.setField(agent, "id", id + 100L);
        ChatRoomAiMember member = ChatRoomAiMember.create(room, agent);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
