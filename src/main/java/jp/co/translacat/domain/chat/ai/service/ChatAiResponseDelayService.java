package jp.co.translacat.domain.chat.ai.service;

import jp.co.translacat.domain.chat.ai.entity.ChatAiSystemSetting;
import jp.co.translacat.domain.chat.ai.enums.ChatAiTriggerType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
public class ChatAiResponseDelayService {

    private static final int BASE_RANDOM_WINDOW_MILLIS = 900;
    private static final int PER_CHARACTER_MILLIS = 4;

    private final ChatAiSystemSettingService systemSettingService;
    private final TaskScheduler taskScheduler;

    public ChatAiResponseDelayService(
            ChatAiSystemSettingService systemSettingService,
            @Qualifier("chatAiResponseDelayScheduler") TaskScheduler taskScheduler
    ) {
        this.systemSettingService = systemSettingService;
        this.taskScheduler = taskScheduler;
    }

    /**
     * MENTION/CONVERSATION의 메시지 저장·WebSocket 전파 시점만 늦춘다.
     * AI Server 호출 자체를 늦추지 않으며 aiExecutor 스레드도 sleep으로 점유하지 않는다.
     * REVIVAL은 이미 시간 단위 스케줄과 랜덤 분산이 있으므로 즉시 delivery를 실행한다.
     */
    public void execute(
            ChatAiTriggerType triggerType,
            String reply,
            Runnable delivery
    ) {
        if (delivery == null) {
            return;
        }
        if (!requiresHumanizedDelay(triggerType)) {
            delivery.run();
            return;
        }

        try {
            ChatAiSystemSetting setting = systemSettingService.getOrCreateEntity();
            if (!setting.isResponseDelayEnabled()) {
                delivery.run();
                return;
            }

            long delayMillis = calculateDelayMillis(
                    setting,
                    reply,
                    ThreadLocalRandom.current().nextDouble()
            );
            log.debug(
                    "Scheduling humanized AI response delivery. trigger={}, delayMillis={}",
                    triggerType,
                    delayMillis
            );
            taskScheduler.schedule(
                    delivery,
                    Instant.now().plusMillis(delayMillis)
            );
        } catch (Exception exception) {
            log.warn(
                    "AI response delay scheduling failed; delivering immediately. trigger={}",
                    triggerType,
                    exception
            );
            delivery.run();
        }
    }

    long calculateDelayMillis(
            ChatAiSystemSetting setting,
            String reply,
            double randomRatio
    ) {
        long min = setting.getResponseDelayMinMillis();
        long max = setting.getResponseDelayMaxMillis();
        if (max <= min) {
            return min;
        }

        int replyLength = reply == null
                ? 0
                : reply.codePointCount(0, reply.length());
        long lengthAwareUpper = min
                + BASE_RANDOM_WINDOW_MILLIS
                + (long) replyLength * PER_CHARACTER_MILLIS;
        long upper = Math.min(max, lengthAwareUpper);
        if (upper <= min) {
            return min;
        }

        double boundedRatio = Math.max(0.0, Math.min(1.0, randomRatio));
        return min + Math.round((upper - min) * boundedRatio);
    }

    private boolean requiresHumanizedDelay(ChatAiTriggerType triggerType) {
        return triggerType == ChatAiTriggerType.MENTION
                || triggerType == ChatAiTriggerType.CONVERSATION;
    }
}
