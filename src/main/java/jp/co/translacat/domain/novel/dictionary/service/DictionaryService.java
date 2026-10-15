package jp.co.translacat.domain.novel.dictionary.service;

import jakarta.annotation.PostConstruct;
import jp.co.translacat.domain.novel.dictionary.dto.DictionaryRegisterDto;
import jp.co.translacat.domain.novel.dictionary.entity.JapaneseDictionary;
import jp.co.translacat.domain.novel.dictionary.repository.JapaneseDictionaryRepository;
import jp.co.translacat.global.utils.TransactionUtil;
import jp.co.translacat.infrastructure.japanese.FuriganaProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DictionaryService {
    private final JapaneseDictionaryRepository repository;
    private final FuriganaProcessor processor;

    @PostConstruct
    public void init() {
        this.refreshCache();
    }

    @Transactional(readOnly = true)
    public void refreshCache() {
        this.processor.syncCachedDict(dictionarySnapshot());
    }

    private Map<String, String> dictionarySnapshot() {
        return repository.findAll().stream()
                // [수정] 문자열 길이가 긴 것부터 정렬 (Descending)
                .sorted(Comparator.comparingInt((JapaneseDictionary d) -> d.getSurface().length()).reversed())
                .collect(Collectors.toMap(
                        JapaneseDictionary::getSurface,
                        JapaneseDictionary::getReading,
                        (v1, v2) -> v1, // 중복 키 발생 시 처리
                        LinkedHashMap::new // 순서 보장을 위해 LinkedHashMap 사용
                ));
    }

    @Transactional
    public Boolean register(DictionaryRegisterDto dictionaryRegisterDto) {
        String surface = dictionaryRegisterDto.getSurface().strip();
        String reading = dictionaryRegisterDto.getReading().strip();
        if (surface.isEmpty() || reading.isEmpty()) {
            throw new IllegalArgumentException("Dictionary surface and reading must not be blank.");
        }

        // 기존 표면형의 발음 교정도 허용하되 같은 단어를 중복 삽입하지 않는다.
        this.repository.findBySurface(surface).ifPresentOrElse(
                entry -> entry.updateReading(reading),
                () -> this.repository.save(JapaneseDictionary.create(surface, reading)));

        // 신규 reader와 구형 표시 API는 원문에서 ruby를 재생성하므로 저장된 회차는 변경하지 않는다.
        // DB commit이 실패했을 때 화면 cache에만 새 발음이 남지 않게 성공 후 교체한다.
        Map<String, String> committedDictionary = dictionarySnapshot();
        TransactionUtil.runAfterCommit(() -> this.processor.syncCachedDict(committedDictionary));

        return true;
    }
}
