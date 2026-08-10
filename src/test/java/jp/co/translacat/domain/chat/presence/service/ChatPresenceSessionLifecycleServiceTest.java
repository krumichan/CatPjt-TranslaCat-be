package jp.co.translacat.domain.chat.presence.service;

import jp.co.translacat.domain.chat.presence.event.ChatPresenceChangedApplicationEvent;
import jp.co.translacat.domain.chat.presence.port.ChatPresenceStore;
import jp.co.translacat.domain.chat.presence.port.ChatPresenceTransitionPublisher;
import jp.co.translacat.domain.chat.presence.scheduler.ChatPresenceOfflineGraceScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatPresenceSessionLifecycleServiceTest {

    @Mock private ChatPresenceStore presenceStore;
    @Mock private ChatPresenceOfflineGraceScheduler offlineGraceScheduler;
    @Mock private ChatPresenceTransitionPublisher transitionPublisher;

    private ChatPresenceLocalSessionRegistry localSessionRegistry;
    private ChatPresenceSessionLifecycleService service;

    @BeforeEach
    void setUp() {
        localSessionRegistry = new ChatPresenceLocalSessionRegistry();
        service = new ChatPresenceSessionLifecycleService(
                presenceStore,
                localSessionRegistry,
                offlineGraceScheduler,
                transitionPublisher
        );
    }

    @Test
    void connected_FirstSharedSession_ClaimsAndPublishesOnline() {
        when(presenceStore.registerSession(100L, "session-a")).thenReturn(1L);
        when(presenceStore.claimOnlineTransition(100L)).thenReturn(true);

        service.connected(100L, "session-a");

        assertEquals(1L, localSessionRegistry.countForUser(100L));
        verify(transitionPublisher).publish(any(ChatPresenceChangedApplicationEvent.class));
    }

    @Test
    void connected_SecondSession_WhenOnlineAlreadyClaimed_DoesNotPublishDuplicateOnline() {
        when(presenceStore.registerSession(100L, "session-a")).thenReturn(1L);
        when(presenceStore.registerSession(100L, "session-b")).thenReturn(2L);
        when(presenceStore.claimOnlineTransition(100L))
                .thenReturn(true)
                .thenReturn(false);

        service.connected(100L, "session-a");
        service.connected(100L, "session-b");

        verify(transitionPublisher, times(1))
                .publish(any(ChatPresenceChangedApplicationEvent.class));
        assertEquals(2L, localSessionRegistry.countForUser(100L));
    }

    @Test
    void connected_DuringOfflineGrace_SharedOnlineStatePreventsDuplicateOnline() {
        when(offlineGraceScheduler.cancel(100L)).thenReturn(true);
        when(presenceStore.registerSession(100L, "session-new")).thenReturn(1L);
        when(presenceStore.claimOnlineTransition(100L)).thenReturn(false);

        service.connected(100L, "session-new");

        verify(offlineGraceScheduler).cancel(100L);
        verify(transitionPublisher, never())
                .publish(any(ChatPresenceChangedApplicationEvent.class));
    }

    @Test
    void disconnected_LastSession_SchedulesGraceAndPublishesOfflineOnlyWhenClaimed() {
        when(presenceStore.registerSession(100L, "session-a")).thenReturn(1L);
        when(presenceStore.claimOnlineTransition(100L)).thenReturn(true);
        service.connected(100L, "session-a");
        when(presenceStore.removeSession(100L, "session-a")).thenReturn(0L);

        service.disconnected("session-a");

        ArgumentCaptor<Runnable> callbackCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(offlineGraceScheduler).schedule(eq(100L), callbackCaptor.capture());

        when(presenceStore.claimOfflineTransitionIfNoActiveSessions(100L))
                .thenReturn(true);
        callbackCaptor.getValue().run();

        ArgumentCaptor<ChatPresenceChangedApplicationEvent> eventCaptor =
                ArgumentCaptor.forClass(ChatPresenceChangedApplicationEvent.class);
        verify(transitionPublisher, times(2)).publish(eventCaptor.capture());

        ChatPresenceChangedApplicationEvent offlineEvent =
                eventCaptor.getAllValues().get(1);
        assertFalse(offlineEvent.online());
        assertEquals(100L, offlineEvent.userId());
    }

    @Test
    void disconnected_GraceVerification_WhenAnotherInstanceReconnected_DoesNotPublishOffline() {
        when(presenceStore.registerSession(100L, "session-a")).thenReturn(1L);
        when(presenceStore.claimOnlineTransition(100L)).thenReturn(true);
        service.connected(100L, "session-a");
        when(presenceStore.removeSession(100L, "session-a")).thenReturn(0L);

        service.disconnected("session-a");

        ArgumentCaptor<Runnable> callbackCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(offlineGraceScheduler).schedule(eq(100L), callbackCaptor.capture());
        when(presenceStore.claimOfflineTransitionIfNoActiveSessions(100L))
                .thenReturn(false);

        callbackCaptor.getValue().run();

        verify(transitionPublisher, times(1))
                .publish(any(ChatPresenceChangedApplicationEvent.class));
    }

    @Test
    void disconnected_WhenAnotherSessionRemains_DoesNotScheduleOffline() {
        when(presenceStore.registerSession(100L, "session-a")).thenReturn(1L);
        when(presenceStore.registerSession(100L, "session-b")).thenReturn(2L);
        when(presenceStore.claimOnlineTransition(100L))
                .thenReturn(true)
                .thenReturn(false);
        service.connected(100L, "session-a");
        service.connected(100L, "session-b");
        when(presenceStore.removeSession(100L, "session-a")).thenReturn(1L);

        service.disconnected("session-a");

        verify(offlineGraceScheduler, never()).schedule(any(), any());
        assertEquals(1L, localSessionRegistry.countForUser(100L));
    }

    @Test
    void refreshLocalSessions_ReregistersLeaseLostByRedisRestartAndClaimsOnlineOnce() {
        when(presenceStore.registerSession(100L, "session-a")).thenReturn(1L);
        when(presenceStore.claimOnlineTransition(100L))
                .thenReturn(true)
                .thenReturn(true);
        service.connected(100L, "session-a");
        when(presenceStore.refreshSession(100L, "session-a")).thenReturn(false);
        when(presenceStore.registerSession(100L, "session-a")).thenReturn(1L);

        service.refreshLocalSessions();

        verify(presenceStore).refreshSession(100L, "session-a");
        verify(presenceStore, times(2)).registerSession(100L, "session-a");
        verify(presenceStore, times(2)).claimOnlineTransition(100L);
        verify(transitionPublisher, times(2))
                .publish(any(ChatPresenceChangedApplicationEvent.class));
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
        verify(offlineGraceScheduler).schedule(eq(100L), any(Runnable.class));
    }
}
