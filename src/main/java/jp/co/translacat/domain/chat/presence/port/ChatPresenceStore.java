package jp.co.translacat.domain.chat.presence.port;

public interface ChatPresenceStore {

    /**
     * Registers or renews a WebSocket presence session and returns the number of
     * non-expired sessions currently known for the user.
     */
    long registerSession(Long userId, String sessionId);

    /**
     * Renews an already registered session lease.
     *
     * @return true when the session existed and was refreshed, false when it had already expired
     */
    boolean refreshSession(Long userId, String sessionId);

    /**
     * Removes the session and returns the number of non-expired sessions that remain for the user.
     */
    long removeSession(Long userId, String sessionId);

    long getActiveSessionCount(Long userId);

    default boolean isOnline(Long userId) {
        return getActiveSessionCount(userId) > 0;
    }
}
