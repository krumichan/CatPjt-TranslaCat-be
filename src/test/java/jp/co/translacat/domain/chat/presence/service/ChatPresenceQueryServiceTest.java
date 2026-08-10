package jp.co.translacat.domain.chat.presence.service;

import jp.co.translacat.domain.chat.presence.config.ChatPresenceProperties;
import jp.co.translacat.domain.chat.presence.port.ChatPresenceStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatPresenceQueryServiceTest {

    @Mock private ChatPresenceStore presenceStore;

    private ChatPresenceProperties properties;
    private ChatPresenceQueryService service;

    @BeforeEach
    void setUp() {
        properties = new ChatPresenceProperties();
        service = new ChatPresenceQueryService(presenceStore, properties);
    }

    @Test
    void resolveOnline_ReturnsUnknownWhenPresenceDisabled() {
        properties.setEnabled(false);

        assertThat(service.resolveOnline(10L)).isNull();
        verify(presenceStore, never()).isOnline(10L);
    }

    @Test
    void resolveOnline_ReturnsUnknownWhenRedisReadFails() {
        properties.setEnabled(true);
        when(presenceStore.isOnline(10L))
                .thenThrow(new IllegalStateException("redis down"));

        assertThat(service.resolveOnline(10L)).isNull();
    }

    @Test
    void resolveOnlineByUserIds_ReturnsSnapshotForDistinctUsers() {
        properties.setEnabled(true);
        when(presenceStore.isOnline(10L)).thenReturn(true);
        when(presenceStore.isOnline(20L)).thenReturn(false);

        Map<Long, Boolean> result = service.resolveOnlineByUserIds(
                List.of(10L, 20L, 10L)
        );

        assertThat(result).containsEntry(10L, true)
                .containsEntry(20L, false)
                .hasSize(2);
    }

    @Test
    void resolveOnlineByUserIds_DegradesWholeSnapshotWhenRedisFails() {
        properties.setEnabled(true);
        when(presenceStore.isOnline(10L)).thenReturn(true);
        when(presenceStore.isOnline(20L))
                .thenThrow(new IllegalStateException("redis down"));

        assertThat(service.resolveOnlineByUserIds(List.of(10L, 20L)))
                .isEmpty();
    }
}
