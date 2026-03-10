package jp.co.translacat.domain.novel.dictionary.service;

import jakarta.annotation.PostConstruct;
import jp.co.translacat.domain.novel.dictionary.dto.DictionaryRegisterDto;
import jp.co.translacat.domain.novel.dictionary.entity.JapaneseDictionary;
import jp.co.translacat.domain.novel.dictionary.repository.JapaneseDictionaryRepository;
import jp.co.translacat.domain.novel.episode.service.EpisodeContentSafeSaver;
import jp.co.translacat.infrastructure.japanese.FuriganaProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DictionaryService {
    private final JapaneseDictionaryRepository repository;
    private final FuriganaProcessor processor;

    private final EpisodeContentSafeSaver episodeServiceSafeSaver;

    @PostConstruct
    public void init() {
        this.refreshCache();
    }

    @Transactional(readOnly = true)
    public void refreshCache() {
        Map<String, String> newDict = repository.findAll().stream()
                // [수정] 문자열 길이가 긴 것부터 정렬 (Descending)
                .sorted(Comparator.comparingInt((JapaneseDictionary d) -> d.getSurface().length()).reversed())
                .collect(Collectors.toMap(
                        JapaneseDictionary::getSurface,
                        JapaneseDictionary::getReading,
                        (v1, v2) -> v1, // 중복 키 발생 시 처리
                        LinkedHashMap::new // 순서 보장을 위해 LinkedHashMap 사용
                ));
        this.processor.syncCachedDict(newDict);
    }

    @Transactional
    public Boolean register(DictionaryRegisterDto dictionaryRegisterDto) {
        String surface = dictionaryRegisterDto.getSurface();
        String reading = dictionaryRegisterDto.getReading();

        // DB 저장.
        this.repository.save(JapaneseDictionary.create(surface, reading));

        // 비동기 DB 갱신.
        this.episodeServiceSafeSaver.updateAllEpisodeContentsWithNewQuery(surface, reading);

        // cache 갱신.
        this.refreshCache();

        return true;
    }
}
