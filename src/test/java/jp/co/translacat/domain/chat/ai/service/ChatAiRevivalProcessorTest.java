package jp.co.translacat.domain.chat.ai.service;

import jp.co.translacat.domain.chat.ai.dto.server.ChatAiReplyRequestDto;
import jp.co.translacat.domain.chat.ai.dto.server.ChatAiResponsePlan;
import jp.co.translacat.domain.chat.ai.dto.server.ChatAiRevivalClaim;
import jp.co.translacat.domain.chat.ai.enums.ChatAiTriggerProcessingResult;
import jp.co.translacat.domain.chat.ai.enums.ChatAiTriggerType;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatAiRevivalProcessorTest {

    @Mock private ChatAiRevivalClaimService claimService;
    @Mock private ChatAiTriggerPlanner triggerPlanner;
    @Mock private ChatAiTriggerProcessor triggerProcessor;

    private ChatAiRevivalProcessor processor;
    private ChatAiRevivalClaim claim;
    private ChatAiResponsePlan plan;

    @BeforeEach
    void setUp() {
        processor = new ChatAiRevivalProcessor(
                claimService,
                triggerPlanner,
                triggerProcessor
        );
        claim = new ChatAiRevivalClaim(
                900L,
                100L,
                10L,
                "token",
                1L,
                1,
                "chat-ai:revival:900:1:1"
        );
        ChatAiReplyRequestDto request = new ChatAiReplyRequestDto(
                claim.requestId(),
                ChatAiTriggerType.REVIVAL,
                new ChatAiReplyRequestDto.Room(
                        100L,
                        ChatRoomType.GROUP,
                        "group",
                        "desc"
                ),
                new ChatAiReplyRequestDto.AiMember(
                        10L,
                        "Mika",
                        "bio",
                        "persona",
                        "ja"
                ),
                null,
                List.of(),
                30,
                12_000,
                800
        );
        plan = new ChatAiResponsePlan(10L, request);
    }

    @Test
    void shouldRespondFalseIsNormalSkipAndStillFinishesBackoffStage() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 10, 12, 0);
        when(claimService.findDueActivityIds(now, 50))
                .thenReturn(List.of(900L));
        when(claimService.claim(900L, now))
                .thenReturn(Optional.of(claim));
        when(triggerPlanner.planRevival(100L, 10L, claim.requestId()))
                .thenReturn(plan);
        when(triggerProcessor.processPlan(plan))
                .thenReturn(ChatAiTriggerProcessingResult.SKIPPED);

        var result = processor.processDue(now, 50);

        assertThat(result.skippedCount()).isEqualTo(1);
        verify(claimService).finish(
                claim,
                ChatAiTriggerProcessingResult.SKIPPED,
                now
        );
    }

    @Test
    void aiFailureIsReportedAsFailedForRetryWithoutAdvancingBackoff() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 10, 12, 0);
        when(claimService.findDueActivityIds(now, 50))
                .thenReturn(List.of(900L));
        when(claimService.claim(900L, now))
                .thenReturn(Optional.of(claim));
        when(triggerPlanner.planRevival(100L, 10L, claim.requestId()))
                .thenReturn(plan);
        when(triggerProcessor.processPlan(plan))
                .thenReturn(ChatAiTriggerProcessingResult.FAILED);

        var result = processor.processDue(now, 50);

        assertThat(result.failedCount()).isEqualTo(1);
        verify(claimService).finish(
                claim,
                ChatAiTriggerProcessingResult.FAILED,
                now
        );
    }
}
