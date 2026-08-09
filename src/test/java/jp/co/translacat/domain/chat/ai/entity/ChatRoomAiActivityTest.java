package jp.co.translacat.domain.chat.ai.entity;

import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ChatRoomAiActivityTest {

    @Test
    void advancesThreeStagesThenStopsAndHumanMessageResetsCycle() {
        ChatRoom room = groupRoom();
        LocalDateTime humanAt = LocalDateTime.of(2026, 8, 9, 10, 0);
        ChatRoomAiActivity activity = ChatRoomAiActivity.create(
                room,
                100L,
                humanAt,
                humanAt.plusHours(24)
        );

        assertThat(activity.getRevivalStage()).isZero();
        assertThat(activity.getRevivalCycleVersion()).isEqualTo(1L);

        LocalDateTime firstAt = humanAt.plusHours(24);
        assertThat(activity.tryClaim("claim-1", firstAt, firstAt.plusMinutes(2)))
                .isTrue();
        assertThat(activity.completeClaimedAttempt(
                "claim-1",
                1L,
                1,
                501L,
                firstAt,
                firstAt.plusHours(72)
        )).isTrue();
        assertThat(activity.getRevivalStage()).isEqualTo(1);
        assertThat(activity.getLastRevivalAiMemberId()).isEqualTo(501L);

        LocalDateTime secondAt = firstAt.plusHours(72);
        assertThat(activity.tryClaim("claim-2", secondAt, secondAt.plusMinutes(2)))
                .isTrue();
        assertThat(activity.completeClaimedAttempt(
                "claim-2",
                1L,
                2,
                502L,
                secondAt,
                secondAt.plusHours(168)
        )).isTrue();

        LocalDateTime thirdAt = secondAt.plusHours(168);
        assertThat(activity.tryClaim("claim-3", thirdAt, thirdAt.plusMinutes(2)))
                .isTrue();
        assertThat(activity.completeClaimedAttempt(
                "claim-3",
                1L,
                3,
                501L,
                thirdAt,
                null
        )).isTrue();
        assertThat(activity.isRevivalStopped()).isTrue();
        assertThat(activity.getNextRevivalAt()).isNull();
        assertThat(activity.getRevivalStage()).isEqualTo(3);

        LocalDateTime newHumanAt = thirdAt.plusHours(1);
        assertThat(activity.resetForHumanMessage(
                101L,
                newHumanAt,
                newHumanAt.plusHours(24)
        )).isTrue();
        assertThat(activity.getRevivalCycleVersion()).isEqualTo(2L);
        assertThat(activity.getRevivalStage()).isZero();
        assertThat(activity.isRevivalStopped()).isFalse();
        assertThat(activity.getLastRevivalAt()).isNull();
        assertThat(activity.getLastRevivalAiMemberId()).isEqualTo(501L);
    }

    @Test
    void activeClaimBlocksDuplicateClaimUntilExpiry() {
        ChatRoom room = groupRoom();
        LocalDateTime dueAt = LocalDateTime.of(2026, 8, 10, 10, 0);
        ChatRoomAiActivity activity = ChatRoomAiActivity.create(
                room,
                100L,
                dueAt.minusHours(24),
                dueAt
        );

        assertThat(activity.tryClaim(
                "claim-1",
                dueAt,
                dueAt.plusMinutes(2)
        )).isTrue();
        assertThat(activity.tryClaim(
                "claim-2",
                dueAt.plusSeconds(1),
                dueAt.plusMinutes(3)
        )).isFalse();
        assertThat(activity.tryClaim(
                "claim-3",
                dueAt.plusMinutes(3),
                dueAt.plusMinutes(5)
        )).isTrue();
    }

    @Test
    void staleHumanEventCannotOverwriteNewerCycle() {
        ChatRoom room = groupRoom();
        LocalDateTime latestAt = LocalDateTime.of(2026, 8, 9, 12, 0);
        ChatRoomAiActivity activity = ChatRoomAiActivity.create(
                room,
                200L,
                latestAt,
                latestAt.plusHours(24)
        );

        assertThat(activity.resetForHumanMessage(
                199L,
                latestAt.minusMinutes(1),
                latestAt.plusHours(23)
        )).isFalse();
        assertThat(activity.getLastHumanMessageId()).isEqualTo(200L);
        assertThat(activity.getRevivalCycleVersion()).isEqualTo(1L);
    }

    private ChatRoom groupRoom() {
        User owner = User.createLocalUser(
                "activity-owner@test.com",
                "password",
                "owner",
                Role.USER,
                "ACTOWNER01"
        );
        ChatRoom room = ChatRoom.createGroupRoom("group", "desc", owner);
        ReflectionTestUtils.setField(room, "id", 1L);
        return room;
    }
}
