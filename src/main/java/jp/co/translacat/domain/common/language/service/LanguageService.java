package jp.co.translacat.domain.common.language.service;

import jp.co.translacat.domain.common.language.dto.RespLanguageDto;
import jp.co.translacat.domain.common.language.repository.LanguageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LanguageService {
    private final LanguageRepository languageRepository;

    public List<RespLanguageDto> languages() {
        return this.languageRepository.findAll()
            .stream().map(RespLanguageDto::of).toList();
    }
}
