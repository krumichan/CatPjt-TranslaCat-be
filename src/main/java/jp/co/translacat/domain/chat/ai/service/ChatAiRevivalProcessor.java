package jp.co.translacat.domain.chat.ai.service;

import jp.co.translacat.domain.chat.ai.dto.server.ChatAiResponsePlan;
import jp.co.translacat.domain.chat.ai.dto.server.ChatAiRevivalClaim;
import jp.co.translacat.domain.chat.ai.enums.ChatAiTriggerProcessingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatAiRevivalProcessor {

    private final ChatAiRevivalClaimService claimService;
    private final ChatAiTriggerPlanner triggerPlanner;
    private final ChatAiTriggerProcessor triggerProcessor;

    public ChatAiRevivalBatchResult processDue(
            LocalDateTime now,
            int limit
    ) {
        List<Long> dueActivityIds = claimService.findDueActivityIds(
                now,
                limit
        );
        int claimedCount = 0;
        int respondedCount = 0;
        int skippedCount = 0;
        int failedCount = 0;

        for (Long activityId : dueActivityIds) {
            Optional<ChatAiRevivalClaim> optionalClaim =
                    claimService.claim(activityId, now);
            if (optionalClaim.isEmpty()) {
                continue;
            }
            claimedCount++;
            ChatAiRevivalClaim claim = optionalClaim.get();

            ChatAiTriggerProcessingResult result = processClaim(claim);
            switch (result) {
                case RESPONDED, DUPLICATE -> respondedCount++;
                case SKIPPED -> skippedCount++;
                case FAILED -> failedCount++;
            }

            try {
                claimService.finish(claim, result, now);
            } catch (Exception exception) {
                log.warn(
                        "AI REVIVAL state completion failed. activityId={}, requestId={}",
                        claim.activityId(),
                        claim.requestId(),
                        exception
                );
            }
        }

        return new ChatAiRevivalBatchResult(
                dueActivityIds.size(),
                claimedCount,
                respondedCount,
                skippedCount,
                failedCount
        );
    }

    private ChatAiTriggerProcessingResult processClaim(
            ChatAiRevivalClaim claim
    ) {
        try {
            ChatAiResponsePlan plan = triggerPlanner.planRevival(
                    claim.roomId(),
                    claim.aiMemberId(),
                    claim.requestId()
            );
            if (plan == null) {
                return ChatAiTriggerProcessingResult.FAILED;
            }
            return triggerProcessor.processPlan(plan);
        } catch (Exception exception) {
            log.warn(
                    "AI REVIVAL processing failed. activityId={}, requestId={}",
                    claim.activityId(),
                    claim.requestId(),
                    exception
            );
            return ChatAiTriggerProcessingResult.FAILED;
        }
    }

    public record ChatAiRevivalBatchResult(
            int dueCount,
            int claimedCount,
            int respondedCount,
            int skippedCount,
            int failedCount
    ) {
    }
}
