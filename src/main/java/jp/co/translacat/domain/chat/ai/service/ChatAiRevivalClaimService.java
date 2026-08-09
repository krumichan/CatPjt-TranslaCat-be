package jp.co.translacat.domain.chat.ai.service;

import jp.co.translacat.domain.chat.ai.dto.server.ChatAiRevivalClaim;
import jp.co.translacat.domain.chat.ai.entity.ChatAiSystemSetting;
import jp.co.translacat.domain.chat.ai.entity.ChatRoomAiActivity;
import jp.co.translacat.domain.chat.ai.entity.ChatRoomAiMember;
import jp.co.translacat.domain.chat.ai.entity.ChatRoomAiSetting;
import jp.co.translacat.domain.chat.ai.enums.ChatAiTriggerProcessingResult;
import jp.co.translacat.domain.chat.ai.repository.ChatRoomAiActivityRepository;
import jp.co.translacat.domain.chat.ai.repository.ChatRoomAiMemberRepository;
import jp.co.translacat.domain.chat.ai.repository.ChatRoomAiSettingRepository;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatAiRevivalClaimService {

    private static final List<ChatRoomType> REVIVAL_ROOM_TYPES = List.of(
            ChatRoomType.GROUP,
            ChatRoomType.OPEN
    );

    private final ChatRoomAiActivityRepository activityRepository;
    private final ChatRoomAiSettingRepository roomSettingRepository;
    private final ChatRoomAiMemberRepository aiMemberRepository;
    private final ChatAiSystemSettingService systemSettingService;
    private final ChatAiRevivalScheduleCalculator scheduleCalculator;

    @Value("${translacat.batch.ai-revival.claim-timeout-seconds:120}")
    private int claimTimeoutSeconds;

    @Value("${translacat.batch.ai-revival.failure-retry-minutes:5}")
    private int failureRetryMinutes;

    @Transactional(readOnly = true)
    public List<Long> findDueActivityIds(
            LocalDateTime now,
            int limit
    ) {
        return activityRepository.findDueActivityIds(
                now,
                REVIVAL_ROOM_TYPES,
                PageRequest.of(0, Math.max(1, limit))
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ChatAiRevivalClaim> claim(
            Long activityId,
            LocalDateTime now
    ) {
        if (activityId == null || now == null) {
            return Optional.empty();
        }

        ChatRoomAiActivity activity = activityRepository
                .findByIdForUpdate(activityId)
                .orElse(null);
        if (activity == null
                || !activity.isDue(now)
                || activity.hasActiveClaim(now)) {
            return Optional.empty();
        }

        activity.clearExpiredClaim(now);

        ChatRoom room = activity.getChatRoom();
        if (!isEligibleRoom(room)) {
            return Optional.empty();
        }

        ChatRoomAiSetting roomSetting = roomSettingRepository
                .findByChatRoomId(room.getId())
                .orElse(null);
        if (roomSetting == null || !roomSetting.isRevivalEnabled()) {
            return Optional.empty();
        }

        ChatAiSystemSetting systemSetting =
                systemSettingService.getOrCreateEntity();
        if (!scheduleCalculator.isWithinAllowedWindow(
                now,
                systemSetting.getRevivalAllowedStartTime(),
                systemSetting.getRevivalAllowedEndTime()
        )) {
            LocalDateTime postponedAt =
                    scheduleCalculator.scheduleAtOrAfter(
                            room.getId(),
                            activity.getRevivalCycleVersion(),
                            activity.getRevivalStage() + 1,
                            now,
                            systemSetting.getRevivalAllowedStartTime(),
                            systemSetting.getRevivalAllowedEndTime()
                    );
            activity.postponeDue(postponedAt);
            return Optional.empty();
        }

        List<ChatRoomAiMember> candidates = aiMemberRepository
                .findByChatRoomIdAndActiveTrueAndDeletedAtIsNullOrderByJoinedAtAsc(
                        room.getId()
                )
                .stream()
                .filter(member -> member.getAiAgent() != null)
                .filter(member -> member.getAiAgent().isActive())
                .filter(member -> !member.getAiAgent().isDeleted())
                .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        ChatRoomAiMember candidate = selectRoundRobinCandidate(
                candidates,
                activity.getLastRevivalAiMemberId()
        );
        int attemptNumber = activity.getRevivalStage() + 1;
        String token = UUID.randomUUID().toString();
        boolean claimed = activity.tryClaim(
                token,
                now,
                now.plusSeconds(Math.max(10, claimTimeoutSeconds))
        );
        if (!claimed) {
            return Optional.empty();
        }

        String requestId = buildRequestId(
                activity.getId(),
                activity.getRevivalCycleVersion(),
                attemptNumber
        );
        return Optional.of(new ChatAiRevivalClaim(
                activity.getId(),
                room.getId(),
                candidate.getId(),
                token,
                activity.getRevivalCycleVersion(),
                attemptNumber,
                requestId
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean finish(
            ChatAiRevivalClaim claim,
            ChatAiTriggerProcessingResult result,
            LocalDateTime finishedAt
    ) {
        if (claim == null || result == null || finishedAt == null) {
            return false;
        }

        ChatRoomAiActivity activity = activityRepository
                .findByIdForUpdate(claim.activityId())
                .orElse(null);
        if (activity == null) {
            return false;
        }

        ChatAiSystemSetting setting = systemSettingService.getOrCreateEntity();
        if (!result.advancesRevivalBackoff()) {
            LocalDateTime retryAt = scheduleCalculator.scheduleRetry(
                    finishedAt,
                    failureRetryMinutes,
                    setting.getRevivalAllowedStartTime(),
                    setting.getRevivalAllowedEndTime()
            );
            return activity.retryClaimedAttempt(
                    claim.claimToken(),
                    claim.cycleVersion(),
                    claim.attemptNumber(),
                    retryAt
            );
        }

        LocalDateTime nextRevivalAt = null;
        if (claim.attemptNumber() < ChatRoomAiActivity.MAX_REVIVAL_STAGE) {
            int delayHours = claim.attemptNumber() == 1
                    ? setting.getRevivalSecondDelayHours()
                    : setting.getRevivalThirdDelayHours();
            nextRevivalAt = scheduleCalculator.scheduleAfterHours(
                    claim.roomId(),
                    claim.cycleVersion(),
                    claim.attemptNumber() + 1,
                    finishedAt,
                    delayHours,
                    setting.getRevivalAllowedStartTime(),
                    setting.getRevivalAllowedEndTime()
            );
        }

        return activity.completeClaimedAttempt(
                claim.claimToken(),
                claim.cycleVersion(),
                claim.attemptNumber(),
                claim.aiMemberId(),
                finishedAt,
                nextRevivalAt
        );
    }

    private ChatRoomAiMember selectRoundRobinCandidate(
            List<ChatRoomAiMember> candidates,
            Long lastAiMemberId
    ) {
        if (lastAiMemberId == null || candidates.size() == 1) {
            return candidates.getFirst();
        }

        for (int index = 0; index < candidates.size(); index++) {
            if (lastAiMemberId.equals(candidates.get(index).getId())) {
                return candidates.get((index + 1) % candidates.size());
            }
        }
        return candidates.getFirst();
    }

    private boolean isEligibleRoom(ChatRoom room) {
        return room != null
                && room.isActive()
                && !room.isDeleted()
                && REVIVAL_ROOM_TYPES.contains(room.getRoomType());
    }

    private String buildRequestId(
            Long activityId,
            long cycleVersion,
            int attemptNumber
    ) {
        return "chat-ai:revival:"
                + activityId
                + ":"
                + cycleVersion
                + ":"
                + attemptNumber;
    }
}
