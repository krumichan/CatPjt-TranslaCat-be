package jp.co.translacat.domain.user.pushinstallation;

import jp.co.translacat.domain.user.auth.session.AuthSession;
import jp.co.translacat.domain.user.auth.session.AuthSessionRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static jp.co.translacat.domain.user.pushinstallation.PushInstallationDtos.*;

@Service
public class PushInstallationService {
    private final PushInstallationRepository installations;
    private final PushRegistryLockRepository guards;
    private final AuthSessionRepository sessions;
    private final PushInstallationProperties properties;
    private final Clock clock;

    public PushInstallationService(PushInstallationRepository installations, PushRegistryLockRepository guards,
                                   AuthSessionRepository sessions, PushInstallationProperties properties,
                                   @Qualifier("pushInstallationClock") Clock clock) {
        this.installations = installations;
        this.guards = guards;
        this.sessions = sessions;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public Registered put(Actor actor, String installationId, Put request) {
        PushInstallationValidation.uuid(installationId);
        PushInstallationValidation.put(request);
        properties.requireAllowed(request.appEnvironment(), request.projectId());
        Instant now = lock(actor);

        // proof와 영속 generation으로 이전 계정의 늦은 PUT/DELETE를 구분한다.
        String proofHash = PushInstallationValidation.hash(request.installationProof());
        var row = installations.findById(installationId).orElse(null);
        String mutation = mutation("PUT", actor, installationId, proofHash, request.provider(), request.expoPushToken(),
                request.platform(), request.appEnvironment(), request.projectId());
        if (replayed(row, proofHash, request.generation(), mutation)) return response(row);

        // 환경/프로젝트를 바꿔도 같은 provider의 정확한 token은 활성 binding 하나에만 연결한다.
        String key = mutation("TOKEN", request.provider(), request.expoPushToken());
        var previous = installations.findByTokenKey(key).orElse(null);
        boolean alreadyCounted = row != null && row.isEnabled() && sameActor(row, actor);
        boolean replacingCounted = previous != null && !previous.getInstallationId().equals(installationId)
                && previous.getUserId() == actor.userId() && live(previous, now);
        long count = installations.countLive(actor.userId(), now);
        if (count - (alreadyCounted ? 1 : 0) - (replacingCounted ? 1 : 0) >= properties.getMaximumActivePerUser()) {
            throw new PushInstallationException(HttpStatus.CONFLICT, "PUSH_DEVICE_LIMIT_REACHED");
        }
        if (previous != null && !previous.getInstallationId().equals(installationId)) {
            previous.disable(now);
            installations.saveAndFlush(previous);
        }

        boolean sameActor = row != null && sameActor(row, actor);
        String binding = sameActor ? row.getAccountBindingId() : UUID.randomUUID().toString();
        boolean sameToken = sameActor && Objects.equals(row.getExpoPushToken(), request.expoPushToken())
                && Objects.equals(row.getProjectId(), request.projectId()) && Objects.equals(row.getAppEnvironment(), request.appEnvironment())
                && Objects.equals(row.getPlatform(), request.platform());
        long version = row == null ? 1 : row.getTokenVersion() + (sameToken ? 0 : 1);
        if (version > PushInstallationValidation.MAX_SAFE_INTEGER) throw PushInstallationException.conflict();
        if (row == null) row = PushInstallation.create(installationId, proofHash, now);
        row.put(actor, request, binding, version, mutation, key, now);
        installations.saveAndFlush(row);
        return response(row);
    }

    @Transactional
    public void delete(Actor actor, String installationId, Delete request) {
        PushInstallationValidation.uuid(installationId);
        if (request == null) throw PushInstallationException.invalid();
        PushInstallationValidation.proof(request.installationProof());
        PushInstallationValidation.generation(request.generation());
        properties.requireConfigured();
        Instant now = lock(actor);

        String proofHash = PushInstallationValidation.hash(request.installationProof());
        var row = installations.findById(installationId).orElse(null);
        String mutation = mutation("DELETE", actor, installationId, proofHash);
        if (row != null && !sameActor(row, actor)) throw PushInstallationException.conflict();
        if (replayed(row, proofHash, request.generation(), mutation)) return;
        // 아직 등록되지 않은 installation의 DELETE도 tombstone으로 남겨 늦은 낮은 generation을 막는다.
        String binding = row != null && sameActor(row, actor) ? row.getAccountBindingId() : UUID.randomUUID().toString();
        if (row == null) row = PushInstallation.create(installationId, proofHash, now);
        row.delete(actor, request.generation(), mutation, binding, now);
        installations.saveAndFlush(row);
    }

    @Transactional(readOnly = true)
    public Targets targets(List<Long> userIds, String environment, String projectId) {
        if (userIds == null || userIds.isEmpty() || userIds.size() > 100 || new HashSet<>(userIds).size() != userIds.size()
                || userIds.stream().anyMatch(value -> value == null || value <= 0)) throw PushInstallationException.invalid();
        PushInstallationValidation.uuid(projectId);
        properties.requireAllowed(environment, projectId);
        // fresh 조회가 현재 살아 있는 sid만 반환한다. 이미 Expo로 넘긴 알림의 회수를 보장하지 않는다.
        var result = installations.findLive(userIds, environment, projectId, clock.instant()).stream()
                .map(row -> new Target(row.getUserId(), row.getInstallationId(), row.getAccountBindingId(),
                        row.getGeneration(), row.getTokenVersion(), row.getExpoPushToken(), row.getPlatform())).toList();
        return new Targets(result);
    }

    @Transactional
    public void invalidate(Invalidate request) {
        if (request == null) throw PushInstallationException.invalid();
        PushInstallationValidation.uuid(request.installationId());
        PushInstallationValidation.uuid(request.accountBindingId());
        PushInstallationValidation.generation(request.generation());
        PushInstallationValidation.generation(request.tokenVersion());
        properties.requireConfigured();
        guards.lockRegistry().orElseThrow(PushInstallationException::unavailable);
        installations.findById(request.installationId()).ifPresent(row -> {
            // 오래된 DeviceNotRegistered receipt가 교체된 토큰·새 계정 binding을 비활성화하지 않는다.
            if (row.isEnabled() && row.getAccountBindingId().equals(request.accountBindingId())
                    && row.getGeneration() == request.generation() && row.getTokenVersion() == request.tokenVersion()) {
                row.disable(clock.instant());
                installations.saveAndFlush(row);
            }
        });
    }

    private Instant lock(Actor actor) {
        if (actor == null || actor.userId() <= 0 || !PushInstallationValidation.isUuid(actor.sid())) throw PushInstallationException.unauthorized();
        // 모든 기기 변경은 registry → sid 순서다. 기존 logout은 sid만 잠그므로 역방향 cycle이 없다.
        guards.lockRegistry().orElseThrow(PushInstallationException::unavailable);
        AuthSession session = sessions.lockByIdAndUserId(actor.sid(), actor.userId()).orElseThrow(PushInstallationException::unauthorized);
        // 락 대기 중 만료될 수 있으므로 요청 시작 시각 대신 락 획득 뒤 현재 시각을 사용한다.
        Instant now = clock.instant();
        if (!session.activeAt(now)) throw PushInstallationException.unauthorized();
        return now;
    }

    private boolean live(PushInstallation row, Instant now) {
        return sessions.existsByIdAndUserIdAndRevokedAtIsNullAndExpiresAtAfter(row.getSessionId(), row.getUserId(), now);
    }

    private boolean replayed(PushInstallation row, String proofHash, long generation, String mutationHash) {
        if (row == null) return false;
        if (!PushInstallationValidation.sameHash(row.getProofHash(), proofHash) || generation < row.getGeneration()
                || (generation == row.getGeneration() && !PushInstallationValidation.sameHash(row.getLastMutationHash(), mutationHash))) {
            throw PushInstallationException.conflict();
        }
        return generation == row.getGeneration();
    }

    private boolean sameActor(PushInstallation row, Actor actor) {
        return row.getUserId() == actor.userId() && row.getSessionId().equals(actor.sid());
    }

    private Registered response(PushInstallation row) {
        return new Registered(row.getInstallationId(), row.getAccountBindingId(), row.getGeneration(), row.getTokenVersion());
    }

    private static String mutation(Object... values) {
        // 길이 접두부가 구분자 충돌을 막고 raw token/proof는 fingerprint 저장 후 남기지 않는다.
        StringBuilder canonical = new StringBuilder();
        for (Object value : values) {
            String text = value instanceof Actor actor ? actor.userId() + ":" + actor.sid() : String.valueOf(value);
            canonical.append(text.length()).append(':').append(text);
        }
        return PushInstallationValidation.hash(canonical.toString());
    }
}
