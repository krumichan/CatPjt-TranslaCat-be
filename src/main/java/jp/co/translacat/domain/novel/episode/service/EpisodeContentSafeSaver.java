package jp.co.translacat.domain.novel.episode.service;

import jp.co.translacat.domain.novel.episode.entity.Episode;
import jp.co.translacat.domain.novel.episode.entity.EpisodeContent;
import jp.co.translacat.domain.novel.episode.respository.EpisodeContentRepository;
import jp.co.translacat.domain.novel.episode.respository.EpisodeRepository;
import jp.co.translacat.infrastructure.japanese.FuriganaProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class EpisodeContentSafeSaver {
    private final EpisodeContentRepository episodeContentRepository;
    private final EpisodeRepository episodeRepository;

    private final FuriganaProcessor processor;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveEpisodeContents(Episode episode, List<EpisodeContent> contents, String expectedSnapshot) {
        // 실패/빈 번역과 잘못된 회차·순번을 기존 데이터 삭제 전에 차단한다.
        if (contents.isEmpty() || expectedSnapshot == null || expectedSnapshot.isBlank()
                || contents.stream().anyMatch(content -> content == null || content.getEpisode() == null
                    || !episode.getId().equals(content.getEpisode().getId()) || content.getContent() == null
                    || (!content.getContent().isBlank()
                        && (content.getContentKo() == null || content.getContentKo().isBlank())))
                || contents.stream().map(EpisodeContent::getSequence).distinct().count() != contents.size()) {
            throw new IllegalStateException("Novel content replacement is incomplete or invalid.");
        }

        // 동일 회차의 저장만 직렬화한다. 모델 호출은 이 짧은 트랜잭션 밖에서 이미 끝났다.
        episodeRepository.findForContentUpdate(episode.getId())
                .orElseThrow(() -> new IllegalStateException("Novel episode is missing."));
        List<EpisodeContent> current = episodeContentRepository.findAllByEpisodeIdOrderBySequenceAsc(episode.getId());
        if (!expectedSnapshot.equals(EpisodeContentSnapshot.fingerprint(current))) {
            throw new IllegalStateException("Novel content changed while translation was running; existing data preserved.");
        }

        // 1. 에피소드에 연결된 모든 내용 제거.
        this.episodeContentRepository.deleteAllByEpisodeId(episode.getId());

        // 2. DELETE 쿼리를 DB에 즉시 전송.
        // -> 3.의 삽입 실행이 먼저 되는 현상 방지를 위함.
        this.episodeContentRepository.flush();

        // 3. 내용 전체를 다시 저장.
        this.episodeContentRepository.batchInsertAll(contents);
    }

    @Async
    @Transactional
    public void updateAllEpisodeContentsWithNewQuery(String surface, String reading) {
        // 대상이 포함된 contents 전부 호출.
        List<EpisodeContent> contents = this.episodeContentRepository.findAllByContentContains(surface);
        if (contents.isEmpty()) {
            return;
        }

        // 1. 프로세서를 사용하여 "동기화된" 대체 문자열 생성
        // 결과 예: <ruby>突き<rt>つき</rt></ruby>刺<rt>さ</rt></ruby>さる
        String synchronizedReplacement = this.processor.resolveDetailedRuby(surface, reading);

        // 2. 정규식 패턴 생성
        StringBuilder patternString = new StringBuilder();
        for (int i = 0; i < surface.length(); i++) {
            patternString.append(Pattern.quote(String.valueOf(surface.charAt(i))));
            if (i < surface.length() - 1) {
                patternString.append("(?:<rt>.*?</rt></ruby><ruby>|<rt>.*?</rt></ruby>|\\s)*");
            }
        }

        String finalRegex = "(?:<ruby>)?" + patternString + "(?:<rt>.*?</rt></ruby>)?";

        // 3. 실행 및 업데이트
        List<EpisodeContent> updatedContents = new ArrayList<>();
        for (EpisodeContent content : contents) {
            String contentJa = content.getContentJa();
            if (contentJa == null || contentJa.isBlank()) continue;

            // 생성한 상세 루비 태그(synchronizedReplacement)로 치환
            String corrected = contentJa.replaceAll(finalRegex, synchronizedReplacement);

            if (!corrected.equals(contentJa)) {
                content.updateContentJa(corrected);
                updatedContents.add(content);
            }
        }

        if (!updatedContents.isEmpty()) {
            this.episodeContentRepository.saveAll(updatedContents);
        }
    }
}
