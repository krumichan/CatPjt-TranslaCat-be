package jp.co.translacat.domain.languagelearning.keyword.service;

import jp.co.translacat.domain.languagelearning.common.enums.KeywordType;
import jp.co.translacat.domain.languagelearning.keyword.dto.request.KeywordCreateRequestDto;
import jp.co.translacat.domain.languagelearning.keyword.dto.request.KeywordUpdateRequestDto;
import jp.co.translacat.domain.languagelearning.keyword.entity.SystemKeyword;
import jp.co.translacat.domain.languagelearning.keyword.policy.KeywordValidationPolicy;
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
    private final KeywordValidationPolicy validationPolicy;

    public SystemKeyword create(KeywordCreateRequestDto request) {
        validationPolicy.validate(request.text(), request.type());

        String normalizedText = KeywordNormalizer.normalize(request.text());
        validateDuplicate(
                null,
                normalizedText,
                request.type()
        );

        SystemKeyword keyword = SystemKeyword.create(
                request.text().trim(),
                normalizedText,
                request.type(),
                validationPolicy.normalizeCanonicalKey(
                        request.canonicalKey(),
                        normalizedText
                )
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
                request.active()
        );

        return keyword;
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
