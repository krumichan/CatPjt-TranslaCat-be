package jp.co.translacat.domain.chat.ai.service;

import jp.co.translacat.domain.chat.ai.dto.server.ChatAiResponsePlan;
import jp.co.translacat.domain.chat.ai.entity.ChatAiAgent;
import jp.co.translacat.domain.chat.ai.entity.ChatAiSystemSetting;
import jp.co.translacat.domain.chat.ai.entity.ChatRoomAiMember;
import jp.co.translacat.domain.chat.ai.entity.ChatRoomAiSetting;
import jp.co.translacat.domain.chat.ai.enums.ChatAiMentionPermission;
import jp.co.translacat.domain.chat.ai.enums.ChatAiTriggerType;
import jp.co.translacat.domain.chat.ai.repository.ChatRoomAiMemberRepository;
import jp.co.translacat.domain.chat.ai.repository.ChatRoomAiSettingRepository;
import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.message.entity.ChatMessage;
import jp.co.translacat.domain.chat.message.enums.ChatMessageSenderType;
import jp.co.translacat.domain.chat.message.enums.ChatMessageStatus;
import jp.co.translacat.domain.chat.message.repository.ChatMessageRepository;
import jp.co.translacat.domain.chat.openchat.profile.service.OpenChatMessageProfileService;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatAiTriggerPlannerTest {

    @Mock private ChatMessageRepository messageRepository;
    @Mock private ChatRoomMemberRepository memberRepository;
    @Mock private ChatRoomAiMemberRepository aiMemberRepository;
    @Mock private ChatRoomAiSettingRepository roomSettingRepository;
    @Mock private ChatAiSystemSettingService systemSettingService;
    @Mock private OpenChatMessageProfileService openChatMessageProfileService;
    @Mock private ChatAiConversationGate conversationGate;

    private ChatAiTriggerPlanner planner;
    private ChatRoom room;
    private User sender;
    private ChatRoomMember senderMember;
    private ChatRoomAiMember aiMember;
    private ChatAiSystemSetting systemSetting;

    @BeforeEach
    void setUp() {
        planner = new ChatAiTriggerPlanner(
                messageRepository,
                memberRepository,
                aiMemberRepository,
                roomSettingRepository,
                systemSettingService,
                openChatMessageProfileService,
                new ChatAiMentionParser(),
                conversationGate
        );

        sender = User.createLocalUser(
                "sender@planner.test",
                "password",
                "sender",
                Role.USER,
                "PLANSENDER1"
        );
        ReflectionTestUtils.setField(sender, "id", 1L);
        room = ChatRoom.createGroupRoom("group", "desc", sender);
        ReflectionTestUtils.setField(room, "id", 100L);
        senderMember = ChatRoomMember.createOwner(
                room,
                sender,
                "ko",
                "ja"
        );
        ReflectionTestUtils.setField(senderMember, "id", 11L);

        ChatAiAgent agent = ChatAiAgent.create(
                "Mika",
                "bio",
                "ja",
                "friendly persona"
        );
        ReflectionTestUtils.setField(agent, "id", 20L);
        aiMember = ChatRoomAiMember.create(room, agent);
        ReflectionTestUtils.setField(aiMember, "id", 30L);
        systemSetting = ChatAiSystemSetting.createDefault();
    }

    @Test
    void mentionHasPriorityAndBuildsPrivacySafeAiServerRequest() {
        ChatMessage trigger = userMessage(200L, "@Mika 일본 여행지 추천해줘");
        ChatRoomAiSetting roomSetting = ChatRoomAiSetting.createDefault(room);
        stubCommon(trigger, roomSetting);
        when(messageRepository
                .findByChatRoomIdAndSenderUserIdAndSenderTypeAndStatusAndDeletedAtIsNullAndCreatedAtGreaterThanEqualAndIdLessThanOrderByIdDesc(
                        eq(100L),
                        eq(1L),
                        eq(ChatMessageSenderType.USER),
                        eq(ChatMessageStatus.SENT),
                        any(LocalDateTime.class),
                        eq(200L)
                )).thenReturn(List.of());
        when(messageRepository
                .findByChatRoomIdAndStatusAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(
                        eq(100L),
                        eq(ChatMessageStatus.SENT),
                        eq(200L),
                        any(Pageable.class)
                )).thenReturn(List.of());

        List<ChatAiResponsePlan> plans = planner.plan(200L);

        assertThat(plans).hasSize(1);
        ChatAiResponsePlan plan = plans.getFirst();
        assertThat(plan.request().triggerType())
                .isEqualTo(ChatAiTriggerType.MENTION);
        assertThat(plan.request().requestId())
                .isEqualTo("chat-ai:mention:200:30");
        assertThat(plan.request().triggerMessage().senderId())
                .isEqualTo("member-1");
        assertThat(plan.request().triggerMessage().senderId())
                .doesNotContain("1L");
        assertThat(plan.request().aiMember().originalLanguageCode())
                .isEqualTo("ja");
        verify(conversationGate, never()).passesResponseRate(anyInt());
    }

    @Test
    void ownerAdminOnlyMentionByMemberDoesNotFallThroughToConversation() {
        senderMember.changeRole(jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole.MEMBER);
        ChatMessage trigger = userMessage(201L, "@Mika 대답해줘");
        ChatRoomAiSetting roomSetting = ChatRoomAiSetting.createDefault(room);
        roomSetting.update(
                null,
                ChatAiMentionPermission.OWNER_ADMIN_ONLY,
                true,
                null
        );
        stubCommon(trigger, roomSetting);

        assertThat(planner.plan(201L)).isEmpty();
        verify(conversationGate, never()).passesResponseRate(anyInt());
    }

    @Test
    void conversationCreatesAtMostOneCandidateAfterGateConditionsPass() {
        ChatMessage trigger = userMessage(202L, "오늘 날씨 좋다");
        ChatRoomAiSetting roomSetting = ChatRoomAiSetting.createDefault(room);
        stubCommon(trigger, roomSetting);
        when(messageRepository
                .findTopByChatRoomIdAndSenderTypeAndStatusAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(
                        100L,
                        ChatMessageSenderType.AI,
                        ChatMessageStatus.SENT,
                        202L
                )).thenReturn(Optional.empty());
        when(messageRepository
                .countByChatRoomIdAndSenderTypeAndStatusAndDeletedAtIsNullAndIdLessThanEqual(
                        100L,
                        ChatMessageSenderType.USER,
                        ChatMessageStatus.SENT,
                        202L
                )).thenReturn(2L);
        when(conversationGate.passesResponseRate(15)).thenReturn(true);
        when(messageRepository
                .findByChatRoomIdAndStatusAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(
                        eq(100L),
                        eq(ChatMessageStatus.SENT),
                        eq(202L),
                        any(Pageable.class)
                )).thenReturn(List.of());

        List<ChatAiResponsePlan> plans = planner.plan(202L);

        assertThat(plans).hasSize(1);
        assertThat(plans.getFirst().request().triggerType())
                .isEqualTo(ChatAiTriggerType.CONVERSATION);
    }

    @Test
    void revivalBuildsRequestWithoutTriggerMessageUsingLatestContext() {
        when(aiMemberRepository
                .findByIdAndChatRoomIdAndActiveTrueAndDeletedAtIsNull(30L, 100L))
                .thenReturn(Optional.of(aiMember));
        when(systemSettingService.getOrCreateEntity())
                .thenReturn(systemSetting);

        ChatMessage latest = userMessage(250L, "오랜만이네");
        when(messageRepository
                .findByChatRoomIdAndStatusAndDeletedAtIsNullOrderByIdDesc(
                        eq(100L),
                        eq(ChatMessageStatus.SENT),
                        any(Pageable.class)
                )).thenReturn(List.of(latest));

        ChatAiResponsePlan plan = planner.planRevival(
                100L,
                30L,
                "chat-ai:revival:900:1:1"
        );

        assertThat(plan).isNotNull();
        assertThat(plan.request().triggerType())
                .isEqualTo(ChatAiTriggerType.REVIVAL);
        assertThat(plan.request().triggerMessage()).isNull();
        assertThat(plan.request().requestId())
                .isEqualTo("chat-ai:revival:900:1:1");
        assertThat(plan.request().contextMessages()).hasSize(1);
        assertThat(plan.request().contextMessages().getFirst().senderId())
                .isEqualTo("member-1");
    }

    private void stubCommon(
            ChatMessage trigger,
            ChatRoomAiSetting roomSetting
    ) {
        when(messageRepository.findByIdAndDeletedAtIsNull(trigger.getId()))
                .thenReturn(Optional.of(trigger));
        when(aiMemberRepository
                .findByChatRoomIdAndActiveTrueAndDeletedAtIsNullOrderByJoinedAtAsc(100L))
                .thenReturn(List.of(aiMember));
        when(roomSettingRepository.findByChatRoomId(100L))
                .thenReturn(Optional.of(roomSetting));
        when(systemSettingService.getOrCreateEntity())
                .thenReturn(systemSetting);
        when(memberRepository
                .findByChatRoomIdAndUserIdAndActiveTrueAndDeletedAtIsNull(100L, 1L))
                .thenReturn(Optional.of(senderMember));
    }

    private ChatMessage userMessage(Long id, String content) {
        ChatMessage message = ChatMessage.createUserTextMessage(
                room,
                sender,
                content
        );
        ReflectionTestUtils.setField(message, "id", id);
        ReflectionTestUtils.setField(
                message,
                "createdAt",
                LocalDateTime.of(2026, 8, 9, 17, 0)
        );
        return message;
    }
}
