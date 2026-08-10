package jp.co.translacat.domain.chat.presence.service;

import jp.co.translacat.domain.chat.presence.event.ChatPresenceChangedApplicationEvent;
import jp.co.translacat.domain.chat.presence.port.ChatPresenceStore;
import jp.co.translacat.domain.chat.presence.scheduler.ChatPresenceOfflineGraceScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatPresenceSessionLifecycleServiceTest {

    @Mock private ChatPresenceStore presenceStore;
    @Mock private ChatPresenceOfflineGraceScheduler offlineGraceScheduler;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ChatPresenceLocalSessionRegistry localSessionRegistry;
    private ChatPresenceSessionLifecycleService service;

    @BeforeEach
    void setUp() {
        localSessionRegistry = new ChatPresenceLocalSessionRegistry();
        service = new ChatPresenceSessionLifecycleService(
                presenceStore,
                localSessionRegistry,
                offlineGraceScheduler,
                eventPublisher
        );
    }

    @Test
    void connected_FirstSharedSession_PublishesOnline() {
        when(presenceStore.registerSession(100L, "session-a")).thenReturn(1L);

        service.connected(100L, "session-a");

        assertEquals(1L, localSessionRegistry.countForUser(100L));
        verify(eventPublisher).publishEvent(any(ChatPresenceChangedApplicationEvent.class));
    }

    @Test
    void connected_SecondSession_DoesNotPublishDuplicateOnline() {
        when(presenceStore.registerSession(100L, "session-a")).thenReturn(1L);
        when(presenceStore.registerSession(100L, "session-b")).thenReturn(2L);

        service.connected(100L, "session-a");
        service.connected(100L, "session-b");

        verify(eventPublisher).publishEvent(any(ChatPresenceChangedApplicationEvent.class));
        assertEquals(2L, localSessionRegistry.countForUser(100L));
    }

    @Test
    void connected_DuringOfflineGrace_CancelsGraceWithoutDuplicateOnline() {
        when(offlineGraceScheduler.cancel(100L)).thenReturn(true);
        when(presenceStore.registerSession(100L, "session-new")).thenReturn(1L);

        service.connected(100L, "session-new");

        verify(offlineGraceScheduler).cancel(100L);
        verify(eventPublisher, never()).publishEvent(any(ChatPresenceChangedApplicationEvent.class));
    }

    @Test
    void disconnected_LastSession_SchedulesGraceAndPublishesOfflineOnlyAfterVerification() {
        when(presenceStore.registerSession(100L, "session-a")).thenReturn(1L);
        service.connected(100L, "session-a");
        when(presenceStore.removeSession(100L, "session-a")).thenReturn(0L);

        service.disconnected("session-a");

        ArgumentCaptor<Runnable> callbackCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(offlineGraceScheduler).schedule(org.mockito.ArgumentMatchers.eq(100L), callbackCaptor.capture());

        when(presenceStore.getActiveSessionCount(100L)).thenReturn(0L);
        callbackCaptor.getValue().run();

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, org.mockito.Mockito.times(2)).publishEvent(eventCaptor.capture());

        ChatPresenceChangedApplicationEvent offlineEvent = (ChatPresenceChangedApplicationEvent) eventCaptor.getAllValues().get(1);
        assertFalse(offlineEvent.online());
        assertEquals(100L, offlineEvent.userId());
    }

    @Test
    void disconnected_WhenAnotherSessionRemains_DoesNotScheduleOffline() {
        when(presenceStore.registerSession(100L, "session-a")).thenReturn(1L);
        when(presenceStore.registerSession(100L, "session-b")).thenReturn(2L);
        service.connected(100L, "session-a");
        service.connected(100L, "session-b");
        when(presenceStore.removeSession(100L, "session-a")).thenReturn(1L);

        service.disconnected("session-a");

        verify(offlineGraceScheduler, never()).schedule(any(), any());
        assertEquals(1L, localSessionRegistry.countForUser(100L));
    }

    @Test
    void refreshLocalSessions_ReregistersLeaseLostByRedisRestart() {
        when(presenceStore.registerSession(100L, "session-a")).thenReturn(1L);
        service.connected(100L, "session-a");
        when(presenceStore.refreshSession(100L, "session-a")).thenReturn(false);
        when(presenceStore.registerSession(100L, "session-a")).thenReturn(1L);

        service.refreshLocalSessions();

        verify(presenceStore).refreshSession(100L, "session-a");
        verify(presenceStore, org.mockito.Mockito.times(2)).registerSession(100L, "session-a");
    }

    @Test
    void redisFailure_DoesNotBreakConnectOrDisconnectCoreFlow() {
        when(presenceStore.registerSession(100L, "session-a"))
                .thenThrow(new IllegalStateException("redis down"));

        assertDoesNotThrow(() -> service.connected(100L, "session-a"));
        assertTrue(localSessionRegistry.countForUser(100L) == 1L);

        when(presenceStore.removeSession(100L, "session-a"))
                .thenThrow(new IllegalStateException("redis down"));

        assertDoesNotThrow(() -> service.disconnected("session-a"));
        assertEquals(0L, localSessionRegistry.countForUser(100L));
        verify(offlineGraceScheduler).schedule(org.mockito.ArgumentMatchers.eq(100L), any(Runnable.class));
    }
}
