package jp.co.translacat.domain.languagelearning.keyword.service;

import jp.co.translacat.domain.languagelearning.keyword.dto.response.KeywordListResponseDto;
import jp.co.translacat.domain.languagelearning.keyword.dto.response.KeywordResponseDto;
import jp.co.translacat.domain.languagelearning.keyword.entity.CustomKeyword;
import jp.co.translacat.domain.languagelearning.keyword.entity.UserSystemKeywordSelection;
import jp.co.translacat.domain.languagelearning.keyword.mapper.KeywordResponseMapper;
import jp.co.translacat.domain.languagelearning.keyword.model.KeywordDisplayName;
import jp.co.translacat.domain.languagelearning.keyword.repository.CustomKeywordRepository;
import jp.co.translacat.domain.languagelearning.keyword.repository.SystemKeywordRepository;
import jp.co.translacat.domain.languagelearning.keyword.repository.UserSystemKeywordSelectionRepository;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningUserSetting;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningUserSettingQueryService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LanguageLearningKeywordQueryService {

    private final SystemKeywordRepository systemKeywordRepository;
    private final CustomKeywordRepository customKeywordRepository;
    private final UserSystemKeywordSelectionRepository selectionRepository;
    private final LanguageLearningUserSettingQueryService settingQueryService;
    private final SystemKeywordDisplayNameResolver displayNameResolver;
    private final KeywordResponseMapper responseMapper;

    @Transactional
    public KeywordListResponseDto getKeywords(
            Long userId,
            String uiLocale
    ) {
        LocalDate today = resolveToday(userId);

        Map<Long, UserSystemKeywordSelection> selections =
                promoteSystemSelections(userId, today);
        List<KeywordResponseDto> systemKeywords =
                mapSystemKeywords(selections, uiLocale);
        List<KeywordResponseDto> customKeywords =
                promoteAndMapCustomKeywords(userId, today);

        return new KeywordListResponseDto(
                systemKeywords,
                customKeywords
        );
    }

    @Transactional(readOnly = true)
    public List<KeywordResponseDto> getSystemKeywordsForAdmin() {
        return systemKeywordRepository.findAllByOrderBySortOrderAscIdAsc()
                .stream()
                .map(keyword -> responseMapper.fromSystem(
                        keyword,
                        false,
                        null
                ))
                .toList();
    }

    public LocalDate resolveToday(Long userId) {
        LanguageLearningUserSetting setting =
                settingQueryService.getOrCreateEntity(userId);
        return settingQueryService.resolveToday(setting);
    }

    private Map<Long, UserSystemKeywordSelection> promoteSystemSelections(
            Long userId,
            LocalDate today
    ) {
        List<UserSystemKeywordSelection> selections =
                selectionRepository.findAllByUserId(userId);

        selections.forEach(selection ->
                selection.promoteIfEffective(today)
        );

        Map<Long, UserSystemKeywordSelection> byKeywordId = new HashMap<>();
        for (UserSystemKeywordSelection selection : selections) {
            byKeywordId.put(
                    selection.getSystemKeyword().getId(),
                    selection
            );
        }

        return byKeywordId;
    }

    private List<KeywordResponseDto> mapSystemKeywords(
            Map<Long, UserSystemKeywordSelection> selections,
            String uiLocale
    ) {
        var keywords = systemKeywordRepository
                .findAllByActiveTrueOrderBySortOrderAscIdAsc();
        Map<Long, KeywordDisplayName> displayNames =
                displayNameResolver.resolve(
                        keywords.stream()
                                .map(keyword -> keyword.getId())
                                .toList(),
                        uiLocale
                );

        return keywords
                .stream()
                .map(keyword -> {
                    UserSystemKeywordSelection selection =
                            selections.get(keyword.getId());

                    return responseMapper.fromSystem(
                            keyword,
                            selection != null && selection.desiredActive(),
                            selection == null
                                    ? null
                                    : selection.getPendingEffectiveDate(),
                            displayNames.get(keyword.getId())
                    );
                })
                .toList();
    }

    private List<KeywordResponseDto> promoteAndMapCustomKeywords(
            Long userId,
            LocalDate today
    ) {
        List<CustomKeyword> keywords =
                customKeywordRepository.findAllByUserIdOrderByIdAsc(userId);

        keywords.forEach(keyword -> keyword.promoteIfEffective(today));

        return keywords.stream()
                .map(responseMapper::fromCustom)
                .toList();
    }
}
