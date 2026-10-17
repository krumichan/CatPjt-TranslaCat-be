package jp.co.translacat.novel.application;

import jp.co.translacat.novel.domain.EpisodeKey;
import org.springframework.stereotype.Service;

@Service
public class NovelFacade {
    private final NovelQueryService query;
    private final NovelCommandService command;
    private final NovelAudioService audio;

    public NovelFacade(NovelQueryService query, NovelCommandService command, NovelAudioService audio) {
        this.query = query;
        this.command = command;
        this.audio = audio;
    }

    public ReaderSnapshot reader(EpisodeKey key, long actor) { return query.reader(key, actor); }
    public ReaderSnapshot reader(EpisodeKey key, long actor, String targetLanguage) { return query.reader(key, actor, targetLanguage); }
    public ReaderSnapshot status(EpisodeKey key, long actor, String jobId) { return query.status(key, actor, jobId); }
    public ReaderSnapshot cancel(EpisodeKey key, long actor, String jobId) { return command.cancel(key, actor, jobId); }
    public ReaderSnapshot start(EpisodeKey key, long actor, String revision, String idempotencyKey, boolean retry) {
        return command.start(key, actor, revision, idempotencyKey, retry);
    }
    public ReaderSnapshot start(EpisodeKey key, long actor, String revision, String idempotencyKey, boolean retry, String targetLanguage) {
        return command.start(key, actor, revision, idempotencyKey, retry, targetLanguage);
    }
    public ReaderSnapshot repair(EpisodeKey key, long actor, String jobId, String revision, String requestKey, String segmentId) {
        return command.repair(key, actor, jobId, revision, requestKey, segmentId);
    }
    public NovelAudioService.Audio audio(EpisodeKey key, long actor, String revision, String segmentId, String language, int partIndex) {
        return audio.synthesize(key, actor, revision, segmentId, language, partIndex);
    }
    public NovelAudioService.Audio audio(EpisodeKey key, long actor, String revision, String segmentId, String language, int partIndex, String targetLanguage) {
        return audio.synthesize(key, actor, revision, segmentId, language, partIndex, targetLanguage);
    }
    public NovelRepository.Glossary glossary(EpisodeKey key, long actor) { return query.glossary(key, actor); }
    public NovelRepository.Glossary glossary(EpisodeKey key, long actor, String targetLanguage) { return query.glossary(key, actor, targetLanguage); }
    public NovelRepository.Glossary glossary(EpisodeKey key, long actor, String expectedVersion, java.util.Map<String, String> terms) {
        return command.glossary(key, actor, expectedVersion, terms);
    }
    public NovelRepository.Glossary glossary(EpisodeKey key, long actor, String expectedVersion, java.util.Map<String, String> terms, String targetLanguage) {
        return command.glossary(key, actor, expectedVersion, terms, targetLanguage);
    }
}
