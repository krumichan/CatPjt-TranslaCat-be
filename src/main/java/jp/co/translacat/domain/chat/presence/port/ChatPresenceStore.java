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

    /**
     * Atomically claims the shared ONLINE transition when at least one live session exists.
     *
     * <p>The Redis implementation keeps a short-lived shared transition state so multiple
     * Backend instances do not emit duplicate ONLINE events for the same logical transition.</p>
     *
     * @return true only for the Backend instance that successfully changed shared state to ONLINE
     */
    boolean claimOnlineTransition(Long userId);

    /**
     * Atomically verifies that no live session exists and claims the shared OFFLINE transition.
     *
     * <p>The active-session check and state transition must be atomic so a reconnect on another
     * Backend instance cannot race with the grace-period verification.</p>
     *
     * @return true only for the Backend instance that successfully changed shared state to OFFLINE
     */
    boolean claimOfflineTransitionIfNoActiveSessions(Long userId);

    default boolean isOnline(Long userId) {
        return getActiveSessionCount(userId) > 0;
    }
}
