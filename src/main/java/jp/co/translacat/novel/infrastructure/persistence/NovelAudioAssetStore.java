package jp.co.translacat.novel.infrastructure.persistence;

import jp.co.translacat.novel.domain.NovelProblem;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/** R2 객체의 소유권과 상태를 기존 BE DataSource에서 짧은 SQL 명령으로 관리한다. */
@Repository
public class NovelAudioAssetStore {
    public record Asset(String id, String identity, String sourceKey, long actor, String language,
                        int partIndex, String textSha256, String model, String objectKey,
                        String checksum, Long bytes, String contentType, Double duration,
                        String state, String owner, long fence, long leaseUntil, int attempts) {}
    public record Claim(Asset asset, boolean owner) {}

    private final JdbcTemplate jdbc;

    public NovelAudioAssetStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Asset> find(String identity) {
        return jdbc.query("SELECT * FROM novel_audio_asset WHERE identity_hash=?",
                this::map, identity).stream().findFirst();
    }

    public Claim claim(String identity, String sourceKey, long actor, String language, int partIndex,
                       String textSha256, String model, String pronunciationVersion, long leaseMillis) {
        long now = System.currentTimeMillis();
        String owner = UUID.randomUUID().toString();
        try {
            // identity unique가 두 BE 인스턴스의 첫 삽입 경합을 하나의 합성으로 묶는다.
            jdbc.update("INSERT INTO novel_audio_asset(asset_id,identity_hash,source_key,actor_id,language,part_index,"
                            + "text_sha256,pronunciation_version,model_name,voice_name,audio_format,state,owner_token,"
                            + "fence,lease_until,attempt_count,created_at,updated_at) "
                            + "VALUES(?,?,?,?,?,?,?,?,?,'marin','wav','GENERATING',?,1,?,1,?,?)",
                    UUID.randomUUID().toString(), identity, sourceKey, actor, language, partIndex,
                    textSha256, pronunciationVersion, model, owner, now + leaseMillis, now, now);
            return new Claim(find(identity).orElseThrow(), true);
        } catch (DuplicateKeyException existing) {
            // 정상 자산·진행 중 자산은 재합성 대상이 아니다. 만료된 upload는 같은 바이트를 복구한다.
            Asset current = find(identity).orElseThrow();
            if ("MISSING".equals(current.state())) {
                int changed = jdbc.update("UPDATE novel_audio_asset SET state='GENERATING',owner_token=?,"
                                + "fence=fence+1,lease_until=?,attempt_count=attempt_count+1,updated_at=? "
                                + "WHERE identity_hash=? AND state='MISSING'",
                        owner, now + leaseMillis, now, identity);
                return new Claim(find(identity).orElseThrow(), changed == 1);
            }
            if ("UPLOAD_FAILED".equals(current.state()) ||
                    "UPLOADING".equals(current.state()) && current.leaseUntil() < now) {
                int changed = jdbc.update("UPDATE novel_audio_asset SET state='UPLOADING',owner_token=?,"
                                + "fence=fence+1,lease_until=?,updated_at=? WHERE identity_hash=? "
                                + "AND (state='UPLOAD_FAILED' OR (state='UPLOADING' AND lease_until<?))",
                        owner, now + leaseMillis, now, identity, now);
                return new Claim(find(identity).orElseThrow(), changed == 1);
            }
            if ("GENERATING".equals(current.state()) && current.leaseUntil() < now) {
                // provider 전송 후 process가 사라진 시도는 자동 재과금하지 않는다.
                jdbc.update("UPDATE novel_audio_asset SET state='UNCERTAIN',owner_token=NULL,lease_until=0,"
                                + "updated_at=? WHERE identity_hash=? AND state='GENERATING' AND lease_until<?",
                        now, identity, now);
                return new Claim(find(identity).orElseThrow(), false);
            }
            return new Claim(current, false);
        }
    }

    public void abandon(Asset owner) {
        jdbc.update("UPDATE novel_audio_asset SET state='MISSING',owner_token=NULL,lease_until=0,updated_at=? "
                        + "WHERE identity_hash=? AND owner_token=? AND fence=? AND state='GENERATING'",
                System.currentTimeMillis(), owner.identity(), owner.owner(), owner.fence());
    }

    public Asset uploading(Asset owner, String objectKey, String checksum, long bytes, String contentType,
                           Double duration, long leaseMillis) {
        int changed = jdbc.update("UPDATE novel_audio_asset SET state='UPLOADING',object_key=?,checksum_sha256=?,"
                        + "bytes=?,content_type=?,duration_seconds=?,lease_until=?,updated_at=? "
                        + "WHERE identity_hash=? AND owner_token=? AND fence=? AND state='GENERATING'",
                objectKey, checksum, bytes, contentType, duration, System.currentTimeMillis() + leaseMillis,
                System.currentTimeMillis(), owner.identity(), owner.owner(), owner.fence());
        if (changed != 1) throw new NovelProblem("AUDIO_CLAIM_LOST", 409);
        return find(owner.identity()).orElseThrow();
    }

    public void ready(Asset owner) {
        int changed = jdbc.update("UPDATE novel_audio_asset SET state='READY',owner_token=NULL,lease_until=0,"
                        + "updated_at=? WHERE identity_hash=? AND owner_token=? AND fence=? AND state='UPLOADING'",
                System.currentTimeMillis(), owner.identity(), owner.owner(), owner.fence());
        if (changed != 1) throw new NovelProblem("AUDIO_CLAIM_LOST", 409);
    }

    public void failed(Asset owner, String state) {
        if (!"UPLOAD_FAILED".equals(state) && !"UNCERTAIN".equals(state)) {
            throw new IllegalArgumentException("Unsupported audio failure state");
        }
        jdbc.update("UPDATE novel_audio_asset SET state=?,owner_token=NULL,lease_until=0,updated_at=? "
                        + "WHERE identity_hash=? AND owner_token=? AND fence=?",
                state, System.currentTimeMillis(), owner.identity(), owner.owner(), owner.fence());
    }

    public void missing(Asset asset) {
        jdbc.update("UPDATE novel_audio_asset SET state='MISSING',updated_at=? "
                        + "WHERE identity_hash=? AND state='READY' AND object_key=?",
                System.currentTimeMillis(), asset.identity(), asset.objectKey());
    }

    public void link(String sourceKey, String revision, long actor, String segmentId,
                     String language, int partIndex, String assetId, String translationCacheKey) {
        // 같은 원문 revision에서 KO가 바뀌면 현재 문장 연결만 새 asset으로 갱신한다.
        int updated = jdbc.update("UPDATE novel_audio_segment_link SET asset_id=?,translation_cache_key=? "
                        + "WHERE source_key=? AND source_revision=? AND actor_id=? AND segment_id=? "
                        + "AND language=? AND part_index=?",
                assetId, translationCacheKey, sourceKey, revision, actor, segmentId, language, partIndex);
        if (updated == 1) return;
        try {
            jdbc.update("INSERT INTO novel_audio_segment_link(source_key,source_revision,actor_id,segment_id,"
                            + "language,part_index,asset_id,translation_cache_key) VALUES(?,?,?,?,?,?,?,?)",
                    sourceKey, revision, actor, segmentId, language, partIndex, assetId, translationCacheKey);
        } catch (DuplicateKeyException race) {
            jdbc.update("UPDATE novel_audio_segment_link SET asset_id=?,translation_cache_key=? "
                            + "WHERE source_key=? AND source_revision=? AND actor_id=? AND segment_id=? "
                            + "AND language=? AND part_index=?",
                    assetId, translationCacheKey, sourceKey, revision, actor, segmentId, language, partIndex);
        }
    }

    private Asset map(ResultSet row, int ignored) throws SQLException {
        long bytes = row.getLong("bytes");
        Long size = row.wasNull() ? null : bytes;
        double duration = row.getDouble("duration_seconds");
        Double seconds = row.wasNull() ? null : duration;
        return new Asset(row.getString("asset_id"), row.getString("identity_hash"),
                row.getString("source_key"), row.getLong("actor_id"), row.getString("language"),
                row.getInt("part_index"), row.getString("text_sha256"), row.getString("model_name"),
                row.getString("object_key"), row.getString("checksum_sha256"), size,
                row.getString("content_type"), seconds, row.getString("state"),
                row.getString("owner_token"), row.getLong("fence"), row.getLong("lease_until"),
                row.getInt("attempt_count"));
    }
}
