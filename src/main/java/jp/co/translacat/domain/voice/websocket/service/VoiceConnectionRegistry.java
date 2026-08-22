package jp.co.translacat.domain.voice.websocket.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class VoiceConnectionRegistry {

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

    public interface GracefulConnection {
        CompletableFuture<Void> closeGracefully(String reason);
    }
}
