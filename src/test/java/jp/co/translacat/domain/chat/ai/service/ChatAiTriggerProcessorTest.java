package jp.co.translacat.domain.chat.ai.service;

import jp.co.translacat.domain.chat.ai.dto.server.ChatAiReplyRequestDto;
import jp.co.translacat.domain.chat.ai.dto.server.ChatAiReplyResponseDto;
import jp.co.translacat.domain.chat.ai.dto.server.ChatAiResponsePlan;
import jp.co.translacat.domain.chat.ai.enums.ChatAiTriggerType;
import jp.co.translacat.domain.chat.ai.port.ChatAiReplyClient;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatAiTriggerProcessorTest {

    @Mock private ChatAiTriggerPlanner planner;
    @Mock private ChatAiReplyClient replyClient;
    @Mock private ChatAiMessageCommandService messageCommandService;

    private ChatAiTriggerProcessor processor;
    private ChatAiResponsePlan plan;

    @BeforeEach
    void setUp() {
        processor = new ChatAiTriggerProcessor(
                planner,
                replyClient,
                messageCommandService
        );

        ChatAiReplyRequestDto request = new ChatAiReplyRequestDto(
                "chat-ai:mention:100:10",
                ChatAiTriggerType.MENTION,
                new ChatAiReplyRequestDto.Room(
                        1L,
                        ChatRoomType.GROUP,
                        "group",
                        "desc"
                ),
                new ChatAiReplyRequestDto.AiMember(
                        10L,
                        "Mika",
                        null,
                        "persona",
                        "ja"
                ),
                new ChatAiReplyRequestDto.TriggerMessage(
                        100L,
                        "member-1",
                        "user",
                        "@Mika hello",
                        LocalDateTime.now()
                ),
                List.of(),
                30,
                12_000,
                800
        );
        plan = new ChatAiResponsePlan(10L, request);
    }

    @Test
    void shouldRespondFalseIsNormalSkip() {
        when(planner.plan(100L)).thenReturn(List.of(plan));
        when(replyClient.generateReply(plan.request()))
                .thenReturn(new ChatAiReplyResponseDto(
                        plan.request().requestId(),
                        false,
                        null,
                        null
                ));

        processor.process(100L);

        verify(messageCommandService, never()).createAiTextMessage(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void validResponseIsSavedThroughCommonAiMessagePipeline() {
        when(planner.plan(100L)).thenReturn(List.of(plan));
        when(replyClient.generateReply(plan.request()))
                .thenReturn(new ChatAiReplyResponseDto(
                        plan.request().requestId(),
                        true,
                        "こんにちは",
                        "ja"
                ));

        processor.process(100L);

        verify(messageCommandService).createAiTextMessage(
                1L,
                10L,
                plan.request().requestId(),
                "こんにちは"
        );
    }

    @Test
    void aiServerFailureDoesNotEscapeProcessor() {
        when(planner.plan(100L)).thenReturn(List.of(plan));
        when(replyClient.generateReply(plan.request()))
                .thenThrow(new RuntimeException("AI server down"));

        processor.process(100L);

        verify(messageCommandService, never()).createAiTextMessage(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void mismatchedRequestIdIsRejected() {
        when(planner.plan(100L)).thenReturn(List.of(plan));
        when(replyClient.generateReply(plan.request()))
                .thenReturn(new ChatAiReplyResponseDto(
                        "different-request",
                        true,
                        "こんにちは",
                        "ja"
                ));

        processor.process(100L);

        verify(messageCommandService, never()).createAiTextMessage(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }
}
