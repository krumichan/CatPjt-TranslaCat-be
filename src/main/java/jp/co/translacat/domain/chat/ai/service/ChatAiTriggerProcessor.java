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
    private final ChatAiResponseDelayService responseDelayService;

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
            processInteractivePlan(plan);
        }
    }

    /**
     * REVIVAL처럼 처리 완료 결과가 즉시 필요한 경로에서 사용한다.
     * 해당 트리거는 Humanized Delay 대상이 아니므로 메시지 저장까지 동기 처리한다.
     */
    public ChatAiTriggerProcessingResult processPlan(
            ChatAiResponsePlan plan
    ) {
        PreparedResponse prepared = prepareResponse(plan);
        if (prepared.result() != null) {
            return prepared.result();
        }
        return persistResponse(prepared);
    }

    private void processInteractivePlan(ChatAiResponsePlan plan) {
        PreparedResponse prepared = prepareResponse(plan);
        if (prepared.result() != null) {
            return;
        }

        responseDelayService.execute(
                prepared.request().triggerType(),
                prepared.response().reply(),
                () -> persistResponse(prepared)
        );
    }

    private PreparedResponse prepareResponse(ChatAiResponsePlan plan) {
        if (plan == null || plan.request() == null) {
            return PreparedResponse.completed(ChatAiTriggerProcessingResult.FAILED);
        }

        ChatAiReplyRequestDto request = plan.request();
        try {
            if (messageCommandService.existsAiMessageByRequestId(
                    request.requestId()
            )) {
                return PreparedResponse.completed(
                        ChatAiTriggerProcessingResult.DUPLICATE
                );
            }

            ChatAiReplyResponseDto response =
                    replyClient.generateReply(request);

            if (!isValidResponse(request, response)) {
                return PreparedResponse.completed(
                        ChatAiTriggerProcessingResult.FAILED
                );
            }
            if (!response.shouldRespond()) {
                log.debug(
                        "AI response skipped by AI Server. requestId={}, trigger={}",
                        request.requestId(),
                        request.triggerType()
                );
                return PreparedResponse.completed(
                        ChatAiTriggerProcessingResult.SKIPPED
                );
            }

            return PreparedResponse.ready(plan, request, response);
        } catch (Exception exception) {
            log.warn(
                    "AI response preparation failed without affecting user message. requestId={}, trigger={}",
                    request.requestId(),
                    request.triggerType(),
                    exception
            );
            return PreparedResponse.completed(
                    ChatAiTriggerProcessingResult.FAILED
            );
        }
    }

    private ChatAiTriggerProcessingResult persistResponse(
            PreparedResponse prepared
    ) {
        ChatAiReplyRequestDto request = prepared.request();
        ChatAiResponsePlan plan = prepared.plan();
        ChatAiReplyResponseDto response = prepared.response();

        try {
            if (messageCommandService.existsAiMessageByRequestId(
                    request.requestId()
            )) {
                return ChatAiTriggerProcessingResult.DUPLICATE;
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
                    "AI response persistence failed without affecting user message. requestId={}, trigger={}",
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

    private record PreparedResponse(
            ChatAiResponsePlan plan,
            ChatAiReplyRequestDto request,
            ChatAiReplyResponseDto response,
            ChatAiTriggerProcessingResult result
    ) {
        private static PreparedResponse ready(
                ChatAiResponsePlan plan,
                ChatAiReplyRequestDto request,
                ChatAiReplyResponseDto response
        ) {
            return new PreparedResponse(plan, request, response, null);
        }

        private static PreparedResponse completed(
                ChatAiTriggerProcessingResult result
        ) {
            return new PreparedResponse(null, null, null, result);
        }
    }
}
