package jp.co.translacat.domain.languagelearning.keyword.service;

import jp.co.translacat.domain.languagelearning.common.enums.KeywordType;
import jp.co.translacat.domain.languagelearning.keyword.dto.request.KeywordCreateRequestDto;
import jp.co.translacat.domain.languagelearning.keyword.dto.request.KeywordUpdateRequestDto;
import jp.co.translacat.domain.languagelearning.keyword.entity.SystemKeyword;
import jp.co.translacat.domain.languagelearning.keyword.policy.KeywordHierarchyPolicy;
import jp.co.translacat.domain.languagelearning.keyword.policy.KeywordValidationPolicy;
import jp.co.translacat.domain.languagelearning.keyword.repository.CustomKeywordRepository;
import jp.co.translacat.domain.languagelearning.keyword.repository.SystemKeywordRepository;
import jp.co.translacat.domain.languagelearning.support.KeywordNormalizer;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class SystemKeywordCommandService {

    private final SystemKeywordRepository systemKeywordRepository;
    private final CustomKeywordRepository customKeywordRepository;
    private final KeywordValidationPolicy validationPolicy;
    private final KeywordHierarchyPolicy hierarchyPolicy;

    public SystemKeyword create(KeywordCreateRequestDto request) {
        validationPolicy.validate(request.text(), request.type());

        String normalizedText = KeywordNormalizer.normalize(request.text());
        validateDuplicate(
                null,
                normalizedText,
                request.type()
        );

        SystemKeyword parentKeyword = resolveParentKeyword(
                request.parentKeywordId()
        );
        hierarchyPolicy.validateSystemHierarchy(
                request.type(),
                parentKeyword,
                null
        );

        SystemKeyword keyword = SystemKeyword.create(
                request.text().trim(),
                normalizedText,
                request.type(),
                validationPolicy.normalizeCanonicalKey(
                        request.canonicalKey(),
                        normalizedText
                ),
                parentKeyword,
                hierarchyPolicy.normalizeSortOrder(request.sortOrder(), 0)
        );

        return systemKeywordRepository.save(keyword);
    }

    public SystemKeyword update(
            Long keywordId,
            KeywordUpdateRequestDto request
    ) {
        SystemKeyword keyword = systemKeywordRepository.findById(keywordId)
                .orElseThrow(validationPolicy::keywordNotFound);

        String desiredText = request.text() == null
                ? keyword.getText()
                : request.text();
        KeywordType desiredType = request.type() == null
                ? keyword.getType()
                : request.type();

        validationPolicy.validate(desiredText, desiredType);

        String normalizedText = KeywordNormalizer.normalize(desiredText);
        validateDuplicate(
                keywordId,
                normalizedText,
                desiredType
        );

        SystemKeyword parentKeyword = resolveParentKeyword(
                request.parentKeywordId()
        );
        hierarchyPolicy.validateSystemHierarchy(
                desiredType,
                parentKeyword,
                keywordId
        );
        validateParentMutation(keyword, desiredType, request.active());

        String fallbackCanonicalKey = keyword.getCanonicalKey() == null
                ? normalizedText
                : keyword.getCanonicalKey();

        keyword.update(
                desiredText.trim(),
                normalizedText,
                desiredType,
                validationPolicy.normalizeCanonicalKey(
                        request.canonicalKey(),
                        fallbackCanonicalKey
                ),
                request.active(),
                parentKeyword,
                hierarchyPolicy.normalizeSortOrder(
                        request.sortOrder(),
                        keyword.getSortOrder()
                )
        );

        return keyword;
    }

    private SystemKeyword resolveParentKeyword(Long parentKeywordId) {
        if (parentKeywordId == null) {
            return null;
        }
        return systemKeywordRepository.findById(parentKeywordId)
                .orElseThrow(hierarchyPolicy::invalidHierarchy);
    }

    private void validateParentMutation(
            SystemKeyword keyword,
            KeywordType desiredType,
            Boolean requestedActive
    ) {
        boolean referencedByCustomKeyword =
                customKeywordRepository.existsByParentSystemKeywordId(
                        keyword.getId()
                ) || customKeywordRepository
                        .existsByPendingParentSystemKeywordId(keyword.getId());

        if (desiredType != KeywordType.TOPIC
                && (systemKeywordRepository.existsByParentKeywordId(
                        keyword.getId()
                ) || referencedByCustomKeyword)) {
            throw hierarchyPolicy.invalidHierarchy();
        }
        if (Boolean.FALSE.equals(requestedActive)
                && (systemKeywordRepository
                        .existsByParentKeywordIdAndActiveTrue(keyword.getId())
                        || referencedByCustomKeyword)) {
            throw hierarchyPolicy.invalidHierarchy();
        }
    }

    private void validateDuplicate(
            Long excludedKeywordId,
            String normalizedText,
            KeywordType type
    ) {
        boolean duplicated = excludedKeywordId == null
                ? systemKeywordRepository.existsByNormalizedTextAndType(
                        normalizedText,
                        type
                )
                : systemKeywordRepository
                        .existsByNormalizedTextAndTypeAndIdNot(
                                normalizedText,
                                type,
                                excludedKeywordId
                        );

        if (duplicated) {
            throw validationPolicy.duplicateKeyword();
        }
    }
}
