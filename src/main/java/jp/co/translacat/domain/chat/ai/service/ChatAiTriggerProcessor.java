package jp.co.translacat.domain.chat.ai.service;

import jp.co.translacat.domain.chat.ai.dto.server.ChatAiReplyRequestDto;
import jp.co.translacat.domain.chat.ai.dto.server.ChatAiReplyResponseDto;
import jp.co.translacat.domain.chat.ai.dto.server.ChatAiResponsePlan;
import jp.co.translacat.domain.chat.ai.enums.ChatAiTriggerProcessingResult;
import jp.co.translacat.domain.chat.ai.port.ChatAiReplyClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatAiTriggerProcessor {

    private final ChatAiTriggerPlanner triggerPlanner;
    private final ChatAiReplyClient replyClient;
    private final ChatAiMessageCommandService messageCommandService;

    public void process(Long triggerMessageId) {
        List<ChatAiResponsePlan> plans;
        try {
            plans = triggerPlanner.plan(triggerMessageId);
        } catch (Exception exception) {
            log.warn(
                    "AI trigger planning failed. messageId={}",
                    triggerMessageId,
                    exception
            );
            return;
        }

        for (ChatAiResponsePlan plan : plans) {
            processPlan(plan);
        }
    }

    public ChatAiTriggerProcessingResult processPlan(
            ChatAiResponsePlan plan
    ) {
        if (plan == null || plan.request() == null) {
            return ChatAiTriggerProcessingResult.FAILED;
        }

        ChatAiReplyRequestDto request = plan.request();
        try {
            if (messageCommandService.existsAiMessageByRequestId(
                    request.requestId()
            )) {
                return ChatAiTriggerProcessingResult.DUPLICATE;
            }

            ChatAiReplyResponseDto response =
                    replyClient.generateReply(request);

            if (!isValidResponse(request, response)) {
                return ChatAiTriggerProcessingResult.FAILED;
            }
            if (!response.shouldRespond()) {
                log.debug(
                        "AI response skipped by AI Server. requestId={}, trigger={}",
                        request.requestId(),
                        request.triggerType()
                );
                return ChatAiTriggerProcessingResult.SKIPPED;
            }

            var createdMessage = messageCommandService.createAiTextMessage(
                    request.room().roomId(),
                    plan.aiMemberId(),
                    request.requestId(),
                    response.reply()
            );
            if (createdMessage != null) {
                return ChatAiTriggerProcessingResult.RESPONDED;
            }
            if (messageCommandService.existsAiMessageByRequestId(
                    request.requestId()
            )) {
                return ChatAiTriggerProcessingResult.DUPLICATE;
            }
            return ChatAiTriggerProcessingResult.FAILED;
        } catch (DataIntegrityViolationException duplicateException) {
            log.info(
                    "Duplicate AI response suppressed by idempotency key. requestId={}",
                    request.requestId()
            );
            return ChatAiTriggerProcessingResult.DUPLICATE;
        } catch (Exception exception) {
            log.warn(
                    "AI response processing failed without affecting user message. requestId={}, trigger={}",
                    request.requestId(),
                    request.triggerType(),
                    exception
            );
            return ChatAiTriggerProcessingResult.FAILED;
        }
    }

    private boolean isValidResponse(
            ChatAiReplyRequestDto request,
            ChatAiReplyResponseDto response
    ) {
        if (response == null) {
            log.warn(
                    "AI Server returned null response. requestId={}",
                    request.requestId()
            );
            return false;
        }
        if (!request.requestId().equals(response.requestId())) {
            log.warn(
                    "AI Server requestId mismatch. expected={}, actual={}",
                    request.requestId(),
                    response.requestId()
            );
            return false;
        }
        if (!response.shouldRespond()) {
            return true;
        }
        if (response.reply() == null || response.reply().isBlank()) {
            log.warn(
                    "AI Server returned empty reply. requestId={}",
                    request.requestId()
            );
            return false;
        }
        String expectedLanguage = request.aiMember()
                .originalLanguageCode();
        if (response.languageCode() == null
                || !expectedLanguage.equalsIgnoreCase(
                response.languageCode()
        )) {
            log.warn(
                    "AI Server language mismatch. requestId={}, expected={}, actual={}",
                    request.requestId(),
                    expectedLanguage,
                    response.languageCode()
            );
            return false;
        }
        return true;
    }
}
