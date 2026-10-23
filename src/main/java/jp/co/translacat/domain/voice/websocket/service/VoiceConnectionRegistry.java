package jp.co.translacat.domain.voice.websocket.service;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.CloseStatus;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class VoiceConnectionRegistry {
    private final JdbcTemplate accounts;

    public VoiceConnectionRegistry() { this.accounts = null; }

    @Autowired
    public VoiceConnectionRegistry(JdbcTemplate accounts) { this.accounts = accounts; }

    public boolean isAccountActive(long userId) {
        // 직접 생성한 격리 relay 테스트는 DB 없이 동작한다. 실제 Spring bean은 위 생성자로 DB를 필수 주입한다.
        if (accounts == null) return true;
        try {
            return accounts.queryForObject("SELECT COUNT(*) FROM `user` WHERE id=? AND account_state='ACTIVE'", Long.class, userId) == 1;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    @Scheduled(fixedDelay = 1000)
    public void closeWithdrawnAccounts() {
        // 다른 BE 인스턴스에서 접수된 탈퇴도 조용한 음성 연결에 적용한다. audio frame마다 DB를 조회하지 않는다.
        var active = new java.util.HashMap<Long, Boolean>();
        sessions.forEach((sessionId, entries) -> {
            boolean close = entries.values().stream().anyMatch(connection -> connection instanceof VoiceRelayConnection relay
                    && !active.computeIfAbsent(relay.context().userId(), this::isAccountActive));
            if (close) forceCloseSession(sessionId);
        });
    }

    private final Map<String, Map<String, GracefulConnection>> sessions =
            new ConcurrentHashMap<>();

    public void register(
            String sessionId,
            String connectionId,
            GracefulConnection connection
    ) {
        sessions.computeIfAbsent(
                        sessionId,
                        ignored -> new ConcurrentHashMap<>()
                )
                .put(connectionId, connection);
    }

    public void remove(
            String sessionId,
            String connectionId
    ) {
        Map<String, GracefulConnection> connections =
                sessions.get(sessionId);
        if (connections == null) {
            return;
        }

        connections.remove(connectionId);
        if (connections.isEmpty()) {
            sessions.remove(sessionId, connections);
        }
    }

    public CompletableFuture<Void> closeSession(
            String sessionId,
            String reason
    ) {
        Map<String, GracefulConnection> current =
                sessions.get(sessionId);
        if (current == null || current.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        var futures = new ArrayList<CompletableFuture<Void>>();
        current.values().forEach(connection ->
                futures.add(connection.closeGracefully(reason))
        );

        return CompletableFuture.allOf(
                futures.toArray(CompletableFuture[]::new)
        );
    }

    public void forceCloseSession(String sessionId) {
        // 먼저 registry에서 분리해 늦은 종료 콜백과 반복 요청이 같은 연결을 재사용하지 않게 한다.
        Map<String, GracefulConnection> current = sessions.remove(sessionId);
        if (current == null) {
            return;
        }

        current.values().forEach(connection ->
                connection.forceClose(CloseStatus.SERVER_ERROR)
        );
    }

    public interface GracefulConnection {
        CompletableFuture<Void> closeGracefully(String reason);

        void forceClose(CloseStatus status);
    }
}
