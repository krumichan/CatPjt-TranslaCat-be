package jp.co.translacat.domain.common.language.service;

import jp.co.translacat.domain.common.language.dto.RespLanguageDto;
import jp.co.translacat.domain.common.language.repository.LanguageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LanguageService {
    private final LanguageRepository languageRepository;

    public List<RespLanguageDto> languages() {
        // 기존 목록의 순서와 표시명을 보존한다. 조회 요청에서 기준 데이터를 쓰지 않는다.
        List<RespLanguageDto> languages = new ArrayList<>(this.languageRepository.findAll()
                .stream().map(RespLanguageDto::of).toList());

        // 수동 영어 seed가 아직 적용되지 않은 DB에서도 영어를 선택할 수 있게 한다.
        if (languages.stream().noneMatch(language -> "en".equalsIgnoreCase(language.getCode()))) {
            languages.add(RespLanguageDto.of("en", "English"));
        }
        return List.copyOf(languages);
    }
}
