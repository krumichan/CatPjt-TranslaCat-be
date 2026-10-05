package jp.co.translacat.novel.application;

import jp.co.translacat.novel.domain.EpisodeKey;
import jp.co.translacat.novel.domain.NovelProblem;
import jp.co.translacat.novel.domain.SentenceSegmenter;
import jp.co.translacat.novel.domain.SourceEpisode;
import jp.co.translacat.novel.domain.SourceText;
import jp.co.translacat.novel.infrastructure.persistence.NovelAudioAssetStore;
import jp.co.translacat.novel.infrastructure.persistence.NovelAudioAssetStore.Asset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** 명시 재생에서만 현재 문장·part를 생성하고 BE DB/R2 자산을 영속 재사용한다. */
@Service
public class NovelAudioService {
    public static final String POLICY_VERSION = "pronunciation-plain-v1-marin-normal";
    private static final int MAX_AUDIO_BYTES = 5_000_000;
    private static final long LEASE_MILLIS = 60_000;

    private final NovelRepository store;
    private final NovelQueryService query;
    private final NovelPorts.Ai ai;
    private final FairAiScheduler scheduler;
    private final NovelAudioAssetStore assets;
    private final NovelAudioObjectStore objects;
    private final Path spoolRoot;
    private final Semaphore synthesisSlots = new Semaphore(2);
    private final Semaphore waitingSlots = new Semaphore(8);
    private final ConcurrentHashMap<String, CompletableFuture<Obtained>> pending = new ConcurrentHashMap<>();

    public NovelAudioService(NovelRepository store, NovelQueryService query, NovelPorts.Ai ai,
                             FairAiScheduler scheduler, NovelAudioAssetStore assets,
                             NovelAudioObjectStore objects,
                             @Value("${novel.audio.spool-dir:./data/novel-audio-spool}") String spoolDirectory) {
        this.store = store;
        this.query = query;
        this.ai = ai;
        this.scheduler = scheduler;
        this.assets = assets;
        this.objects = objects;
        this.spoolRoot = Path.of(spoolDirectory).toAbsolutePath().normalize();
    }

    public record Audio(String revision, String segmentId, String language, int partIndex, int partCount,
                        String audioBase64, String contentType, Double durationSeconds, String provider,
                        String model, String audioPolicyVersion, String traceId,
                        String cacheDisposition, Map<String, Object> timings) {}

    private record Obtained(Asset asset, byte[] bytes, String disposition) {}

    public Audio synthesize(EpisodeKey key, long actor, String revision, String segmentId,
                            String language, int partIndex) {
        long started = System.nanoTime();
        String traceId = RequestTrace.idOrNew();

        // 현재 원문과 actor의 확정 번역만 낭독 대상으로 삼는다. 목록·제목은 이 경로가 없다.
        SourceEpisode source = query.requireCurrentRevision(key, revision);
        SourceEpisode.Segment segment = source.segments().stream()
                .filter(item -> item.id().equals(segmentId)).findFirst()
                .orElseThrow(() -> new NovelProblem("SEGMENT_NOT_FOUND", 404));
        String translationKey = null;
        String text;
        if ("ja".equals(language)) {
            text = segment.plainJa();
        } else if ("ko".equals(language)) {
            translationKey = query.cacheKey(key, source, actor, store.glossary(key, actor).version());
            String finalKey = translationKey;
            text = store.byCache(finalKey).map(job -> job.results().get(segmentId))
                    .orElseThrow(() -> new NovelProblem("TRANSLATION_NOT_READY", 409));
        } else {
            throw new NovelProblem("AUDIO_LANGUAGE_INVALID", 400);
        }
        List<String> parts = speechParts(text);
        if (partIndex < 0 || partIndex >= parts.size()) throw new NovelProblem("AUDIO_PART_INVALID", 400);

        // 회차 revision·재생 속도는 음성을 바꾸지 않는다. 동일 actor의 바뀌지 않은 문장은 재사용한다.
        String spoken = parts.get(partIndex);
        String textHash = SentenceSegmenter.hash(spoken);
        String identity = SentenceSegmenter.hash(String.join("|", key.value(), Long.toString(actor),
                language, Integer.toString(partIndex), textHash, POLICY_VERSION, ai.speechModel(), "wav"));
        Obtained result = getOrCreate(identity, key.value(), actor, language, partIndex,
                spoken, textHash, traceId);
        assets.link(key.value(), revision, actor, segmentId, language, partIndex,
                result.asset().id(), translationKey);

        return new Audio(revision, segmentId, language, partIndex, parts.size(),
                Base64.getEncoder().encodeToString(result.bytes()), result.asset().contentType(),
                result.asset().duration(), "openai", result.asset().model(),
                POLICY_VERSION + "|" + ai.speechModel(), traceId, result.disposition(),
                Map.of("requestMs", RequestTrace.elapsed(started)));
    }

    private Obtained getOrCreate(String identity, String sourceKey, long actor, String language,
                                 int partIndex, String spoken, String textHash, String traceId) {
        // DB/R2 READY는 프로세스 cache보다 우선한다. 404만 현재 클릭의 재생성 대상으로 바꾼다.
        Asset current = assets.find(identity).orElse(null);
        if (current != null && "READY".equals(current.state())) {
            try {
                return read(current, "HIT");
            } catch (NovelAudioObjectStore.Missing missing) {
                assets.missing(current);
            }
        }

        CompletableFuture<Obtained> future = new CompletableFuture<>();
        CompletableFuture<Obtained> other = pending.putIfAbsent(identity, future);
        if (other != null) {
            // 동일 음성 클릭도 대기 중인 BE 요청 수를 제한한다. 소유자의 합성 슬롯과 별도로 센다.
            if (!waitingSlots.tryAcquire()) throw new NovelProblem("AUDIO_QUEUE_FULL", 429, true, 1000);
            try {
                Obtained shared = other.get(31, TimeUnit.SECONDS);
                return new Obtained(shared.asset(), shared.bytes(), "COALESCED");
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new NovelProblem("AUDIO_INTERRUPTED", 503);
            } catch (Exception failure) {
                if (failure.getCause() instanceof NovelProblem problem) throw problem;
                throw new NovelProblem("AUDIO_GENERATION_FAILED", 502);
            } finally {
                waitingSlots.release();
            }
        }

        try {
            Obtained obtained = ownedOrWait(identity, sourceKey, actor, language, partIndex,
                    spoken, textHash, traceId);
            future.complete(obtained);
            return obtained;
        } catch (RuntimeException failure) {
            future.completeExceptionally(failure);
            throw failure;
        } finally {
            pending.remove(identity, future);
        }
    }

    private Obtained ownedOrWait(String identity, String sourceKey, long actor, String language,
                                 int partIndex, String spoken, String textHash, String traceId) {
        var claim = assets.claim(identity, sourceKey, actor, language, partIndex, textHash,
                ai.speechModel(), POLICY_VERSION, LEASE_MILLIS);
        Asset asset = claim.asset();
        if (!claim.owner()) {
            if ("READY".equals(asset.state())) return read(asset, "HIT");
            if ("UNCERTAIN".equals(asset.state())) throw new NovelProblem("AUDIO_OUTCOME_UNCERTAIN", 409);
            return waitForOwner(identity);
        }
        if ("UPLOADING".equals(asset.state())) return recoverUpload(asset);
        return generate(asset, spoken, language, traceId);
    }

    private Obtained waitForOwner(String identity) {
        if (!waitingSlots.tryAcquire()) throw new NovelProblem("AUDIO_QUEUE_FULL", 429, true, 1000);
        try {
            for (int i = 0; i < 150; i++) {
                Asset current = assets.find(identity).orElseThrow();
                if ("READY".equals(current.state())) return read(current, "COALESCED");
                if ("UNCERTAIN".equals(current.state()) || "UPLOAD_FAILED".equals(current.state())) {
                    throw new NovelProblem("AUDIO_GENERATION_FAILED", 503, true, 1000);
                }
                try { Thread.sleep(200); }
                catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new NovelProblem("AUDIO_INTERRUPTED", 503);
                }
            }
            throw new NovelProblem("AUDIO_TIMEOUT", 504, true, 1000);
        } finally {
            waitingSlots.release();
        }
    }

    private Obtained generate(Asset owner, String spoken, String language, String traceId) {
        if (!synthesisSlots.tryAcquire()) {
            assets.abandon(owner);
            throw new NovelProblem("AUDIO_QUEUE_FULL", 429, true, 1000);
        }
        boolean providerStarted = false;
        try {
            // DB lease와 짧은 쓰기 트랜잭션만 사용하며 유료 AI 호출 동안 connection을 잡지 않는다.
            var running = scheduler.submitImmediate("audio", 2,
                    System.nanoTime() + TimeUnit.SECONDS.toNanos(30), ignored -> {},
                    () -> ai.synthesize(traceId, UUID.randomUUID().toString(), spoken, language, 30_000));
            providerStarted = true;
            NovelPorts.Speech speech;
            try { speech = running.get(30, TimeUnit.SECONDS); }
            catch (Exception failure) {
                running.cancel(true);
                if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new NovelProblem("AUDIO_GENERATION_FAILED", 502);
            }
            byte[] bytes = decode(speech);
            String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            String objectKey = objectKey(owner);
            Path spool = spool(owner);

            // 합성된 동일 바이트를 디스크에 먼저 고정해 업로드 실패 시 provider를 반복하지 않는다.
            reserveSpool(bytes.length);
            Files.write(spool, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            Asset uploading = assets.uploading(owner, objectKey, checksum, bytes.length,
                    speech.contentType(), speech.durationSeconds(), LEASE_MILLIS);
            try {
                objects.put(objectKey, spool, speech.contentType(), bytes.length, checksum);
                verifyObject(uploading);
            } catch (RuntimeException failure) {
                assets.failed(uploading, "UPLOAD_FAILED");
                throw failure;
            }
            assets.ready(uploading);
            Files.deleteIfExists(spool);
            return read(assets.find(owner.identity()).orElseThrow(), "MISS");
        } catch (NovelProblem failure) {
            if (assets.find(owner.identity()).map(Asset::state).filter("GENERATING"::equals).isPresent()) {
                if (providerStarted) assets.failed(owner, "UNCERTAIN");
                else assets.abandon(owner);
            }
            throw failure;
        } catch (Exception failure) {
            if (assets.find(owner.identity()).map(Asset::state).filter("GENERATING"::equals).isPresent()) {
                if (providerStarted) assets.failed(owner, "UNCERTAIN");
                else assets.abandon(owner);
            }
            throw new NovelProblem("AUDIO_GENERATION_FAILED", 502);
        } finally {
            synthesisSlots.release();
        }
    }

    private Obtained recoverUpload(Asset owner) {
        Path spool = spool(owner);
        try {
            // DB 확정 실패 뒤 R2 객체가 이미 있다면 HEAD의 크기/실제 저장 checksum으로 같은 자산을 확정한다.
            try {
                verifyObject(owner);
            } catch (NovelAudioObjectStore.Missing missing) {
                if (!Files.isRegularFile(spool) || Files.size(spool) != owner.bytes()
                        || !checksum(Files.readAllBytes(spool)).equals(owner.checksum())) {
                    assets.failed(owner, "UNCERTAIN");
                    throw new NovelProblem("AUDIO_BYTES_UNAVAILABLE", 409);
                }
                objects.put(owner.objectKey(), spool, owner.contentType(), owner.bytes(), owner.checksum());
                verifyObject(owner);
            }
            assets.ready(owner);
            Files.deleteIfExists(spool);
            return read(assets.find(owner.identity()).orElseThrow(), "RECOVERED");
        } catch (NovelProblem failure) {
            throw failure;
        } catch (Exception failure) {
            throw new NovelProblem("AUDIO_STORAGE_UNAVAILABLE", 503, true, 1000);
        }
    }

    private void verifyObject(Asset asset) {
        var info = objects.head(asset.objectKey());
        if (asset.bytes() == null || info.bytes() != asset.bytes()
                || !asset.checksum().equals(info.sha256())
                || !asset.contentType().equals(info.contentType())) {
            throw new NovelProblem("AUDIO_OBJECT_INVALID", 502);
        }
    }

    private Obtained read(Asset asset, String disposition) {
        var data = objects.read(asset.objectKey(), MAX_AUDIO_BYTES);
        if (asset.bytes() == null || data.bytes().length != asset.bytes()
                || !asset.contentType().equals(data.contentType())
                || !asset.checksum().equals(checksum(data.bytes()))) {
            throw new NovelProblem("AUDIO_OBJECT_INVALID", 502);
        }
        return new Obtained(asset, data.bytes(), disposition);
    }

    private Path spool(Asset asset) {
        Path path = spoolRoot.resolve(asset.identity() + "-" + asset.attempts() + ".wav").normalize();
        if (!path.startsWith(spoolRoot)) throw new NovelProblem("AUDIO_SPOOL_INVALID", 503);
        return path;
    }

    private void reserveSpool(int newBytes) throws Exception {
        Files.createDirectories(spoolRoot);
        try (var entries = Files.list(spoolRoot)) {
            var files = entries.filter(Files::isRegularFile).toList();
            long bytes = 0;
            for (Path file : files) bytes += Files.size(file);
            if (files.size() >= 128 || bytes + newBytes > 32_000_000L) {
                throw new NovelProblem("AUDIO_SPOOL_FULL", 503);
            }
        }
    }

    private static String objectKey(Asset asset) {
        return "novel/audio/v1/" + asset.identity() + "/" + asset.attempts() + ".wav";
    }

    private static byte[] decode(NovelPorts.Speech speech) {
        try {
            if (!"audio/wav".equals(speech.contentType()) || speech.audioBase64().length() > 6_700_000) {
                throw new NovelProblem("AUDIO_RESPONSE_INVALID", 502);
            }
            byte[] data = Base64.getDecoder().decode(speech.audioBase64());
            if (data.length < 44 || data.length > MAX_AUDIO_BYTES || data[0] != 'R' || data[1] != 'I'
                    || data[2] != 'F' || data[3] != 'F' || data[8] != 'W' || data[9] != 'A'
                    || data[10] != 'V' || data[11] != 'E') throw new NovelProblem("AUDIO_RESPONSE_INVALID", 502);
            return data;
        } catch (IllegalArgumentException failure) {
            throw new NovelProblem("AUDIO_RESPONSE_INVALID", 502);
        }
    }

    private static String checksum(byte[] data) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
        catch (Exception failure) { throw new IllegalStateException("SHA256_UNAVAILABLE"); }
    }

    public static List<String> speechParts(String text) {
        if (text == null || SourceText.isBlank(text) || text.length() > 12_000) {
            throw new NovelProblem("AUDIO_TEXT_INVALID", 422);
        }
        List<String> parts = new ArrayList<>();
        int start = 0;
        int bytes = 0;
        for (int offset = 0; offset < text.length();) {
            int codepoint = text.codePointAt(offset);
            int size = new String(Character.toChars(codepoint)).getBytes(StandardCharsets.UTF_8).length;
            if (bytes + size > 1200) {
                parts.add(text.substring(start, offset));
                start = offset;
                bytes = 0;
            }
            bytes += size;
            offset += Character.charCount(codepoint);
        }
        parts.add(text.substring(start));
        if (parts.size() > 32) throw new NovelProblem("AUDIO_PART_LIMIT", 413);
        return parts;
    }
}
