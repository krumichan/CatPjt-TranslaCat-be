package jp.co.translacat.domain.user.pushinstallation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "push_installation", indexes = @Index(name = "ix_push_installation_user", columnList = "user_id,enabled"))
public class PushInstallation {
    @Id @Column(name = "installation_id", length = 36, nullable = false, updatable = false)
    private String installationId;
    @Column(name = "proof_hash", length = 64, nullable = false, updatable = false)
    private String proofHash;
    @Column(name = "user_id", nullable = false)
    private long userId;
    @Column(name = "session_id", length = 36, nullable = false)
    private String sessionId;
    @Column(name = "account_binding_id", length = 36, nullable = false)
    private String accountBindingId;
    @Column(nullable = false)
    private long generation;
    @Column(name = "token_version", nullable = false)
    private long tokenVersion;
    @Column(name = "last_mutation_hash", length = 64, nullable = false)
    private String lastMutationHash;
    @Column(length = 16)
    private String provider;
    @Column(name = "expo_push_token", length = 280)
    private String expoPushToken;
    @Column(name = "token_key", length = 64, unique = true)
    private String tokenKey;
    @Column(length = 8)
    private String platform;
    @Column(name = "app_environment", length = 16)
    private String appEnvironment;
    @Column(name = "project_id", length = 36)
    private String projectId;
    @Column(nullable = false)
    private boolean enabled;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    static PushInstallation create(String id, String proofHash, Instant now) {
        var row = new PushInstallation();
        row.installationId = id;
        row.proofHash = proofHash;
        row.createdAt = now;
        return row;
    }

    void put(PushInstallationDtos.Actor actor, PushInstallationDtos.Put request, String binding,
             long version, String mutationHash, String key, Instant now) {
        userId = actor.userId();
        sessionId = actor.sid();
        accountBindingId = binding;
        generation = request.generation();
        tokenVersion = version;
        lastMutationHash = mutationHash;
        provider = request.provider();
        expoPushToken = request.expoPushToken();
        tokenKey = key;
        platform = request.platform();
        appEnvironment = request.appEnvironment();
        projectId = request.projectId();
        enabled = true;
        updatedAt = now;
    }

    void delete(PushInstallationDtos.Actor actor, long acceptedGeneration, String mutationHash,
                String binding, Instant now) {
        userId = actor.userId();
        sessionId = actor.sid();
        accountBindingId = binding;
        generation = acceptedGeneration;
        if (tokenVersion == 0) tokenVersion = 1;
        lastMutationHash = mutationHash;
        disable(now);
    }

    void disable(Instant now) {
        enabled = false;
        expoPushToken = null;
        tokenKey = null;
        updatedAt = now;
    }
}
