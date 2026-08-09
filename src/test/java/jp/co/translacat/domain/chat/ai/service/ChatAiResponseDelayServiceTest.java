package jp.co.translacat.domain.chat.ai.service;

import jp.co.translacat.domain.chat.ai.entity.ChatAiSystemSetting;
import jp.co.translacat.domain.chat.ai.enums.ChatAiTriggerType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatAiResponseDelayServiceTest {

    @Mock private ChatAiSystemSettingService systemSettingService;
    @Mock private TaskScheduler taskScheduler;

    private ChatAiResponseDelayService service;

    @BeforeEach
    void setUp() {
        service = new ChatAiResponseDelayService(
                systemSettingService,
                taskScheduler
        );
    }

    @Test
    void mentionSchedulesDeliveryInConfiguredWindow() {
        ChatAiSystemSetting setting = ChatAiSystemSetting.createDefault();
        when(systemSettingService.getOrCreateEntity()).thenReturn(setting);
        Runnable delivery = () -> { };
        Instant before = Instant.now();

        service.execute(ChatAiTriggerType.MENTION, "こんにちは！", delivery);

        ArgumentCaptor<Instant> instantCaptor =
                ArgumentCaptor.forClass(Instant.class);
        verify(taskScheduler).schedule(
                org.mockito.ArgumentMatchers.eq(delivery),
                instantCaptor.capture()
        );
        long delay = instantCaptor.getValue().toEpochMilli()
                - before.toEpochMilli();
        assertThat(delay).isBetween(
                (long) ChatAiSystemSetting.DEFAULT_RESPONSE_DELAY_MIN_MILLIS,
                (long) ChatAiSystemSetting.DEFAULT_RESPONSE_DELAY_MAX_MILLIS + 100L
        );
    }

    @Test
    void revivalRunsDeliveryImmediatelyWithoutScheduler() {
        AtomicBoolean delivered = new AtomicBoolean(false);

        service.execute(
                ChatAiTriggerType.REVIVAL,
                "久しぶり！",
                () -> delivered.set(true)
        );

        assertThat(delivered).isTrue();
        verify(systemSettingService, never()).getOrCreateEntity();
        verify(taskScheduler, never()).schedule(
                any(Runnable.class),
                any(Instant.class)
        );
    }

    @Test
    void disabledSettingRunsDeliveryImmediately() {
        ChatAiSystemSetting setting = ChatAiSystemSetting.createDefault();
        setting.update(
                null, null, null, null,
                false, 1_200, 3_500,
                null, null, null, null, null,
                null, null, null, null, null
        );
        when(systemSettingService.getOrCreateEntity()).thenReturn(setting);
        AtomicBoolean delivered = new AtomicBoolean(false);

        service.execute(
                ChatAiTriggerType.CONVERSATION,
                "hello",
                () -> delivered.set(true)
        );

        assertThat(delivered).isTrue();
        verify(taskScheduler, never()).schedule(
                any(Runnable.class),
                any(Instant.class)
        );
    }

    @Test
    void longerReplyRaisesPossibleUpperBoundWithoutExceedingConfiguredMax() {
        ChatAiSystemSetting setting = ChatAiSystemSetting.createDefault();

        long shortDelay = service.calculateDelayMillis(setting, "짧아", 1.0);
        long longDelay = service.calculateDelayMillis(
                setting,
                "가".repeat(800),
                1.0
        );

        assertThat(shortDelay).isLessThan(longDelay);
        assertThat(longDelay)
                .isEqualTo(ChatAiSystemSetting.DEFAULT_RESPONSE_DELAY_MAX_MILLIS);
    }
}
