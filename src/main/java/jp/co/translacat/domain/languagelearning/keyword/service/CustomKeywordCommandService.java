package jp.co.translacat.domain.languagelearning.keyword.service;

import jp.co.translacat.domain.languagelearning.common.enums.KeywordType;
import jp.co.translacat.domain.languagelearning.keyword.dto.request.KeywordCreateRequestDto;
import jp.co.translacat.domain.languagelearning.keyword.dto.request.KeywordUpdateRequestDto;
import jp.co.translacat.domain.languagelearning.keyword.entity.CustomKeyword;
import jp.co.translacat.domain.languagelearning.keyword.entity.SystemKeyword;
import jp.co.translacat.domain.languagelearning.keyword.policy.KeywordApplicationTimingPolicy;
import jp.co.translacat.domain.languagelearning.keyword.policy.KeywordHierarchyPolicy;
import jp.co.translacat.domain.languagelearning.keyword.policy.KeywordValidationPolicy;
import jp.co.translacat.domain.languagelearning.keyword.repository.CustomKeywordRepository;
import jp.co.translacat.domain.languagelearning.keyword.repository.SystemKeywordRepository;
import jp.co.translacat.domain.languagelearning.support.KeywordNormalizer;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional
public class CustomKeywordCommandService {

    private final CustomKeywordRepository customKeywordRepository;
    private final UserRepository userRepository;
    private final LanguageLearningKeywordQueryService keywordQueryService;
    private final KeywordValidationPolicy validationPolicy;
    private final KeywordHierarchyPolicy hierarchyPolicy;
    private final KeywordApplicationTimingPolicy applicationTimingPolicy;
    private final SystemKeywordRepository systemKeywordRepository;

    public CustomKeyword create(
            Long userId,
            KeywordCreateRequestDto request
    ) {
        validationPolicy.validate(request.text(), request.type());

        String normalizedText = KeywordNormalizer.normalize(request.text());
        validateDuplicate(
                userId,
                null,
                normalizedText,
                request.type()
        );

        User user = getUser(userId);
        LocalDate today = keywordQueryService.resolveToday(userId);
        LocalDate effectiveDate = applicationTimingPolicy.resolveEffectiveDate(
                userId,
                today
        );
        String canonicalKey = validationPolicy.normalizeCanonicalKey(
                request.canonicalKey(),
                normalizedText
        );
        SystemKeyword parentKeyword = resolveParentKeyword(
                request.parentKeywordId()
        );
        hierarchyPolicy.validateCustomHierarchy(
                request.type(),
                parentKeyword
        );

        CustomKeyword keyword = CustomKeyword.create(
                user,
                request.text().trim(),
                normalizedText,
                request.type(),
                canonicalKey,
                parentKeyword,
                effectiveDate
        );
        keyword.promoteIfEffective(today);

        return customKeywordRepository.save(keyword);
    }

    public CustomKeyword update(
            Long userId,
            Long keywordId,
            KeywordUpdateRequestDto request
    ) {
        CustomKeyword keyword = getOwnedKeyword(userId, keywordId);
        LocalDate today = keywordQueryService.resolveToday(userId);

        keyword.promoteIfEffective(today);

        String desiredText = request.text() == null
                ? keyword.desiredText()
                : request.text();
        KeywordType desiredType = request.type() == null
                ? keyword.desiredType()
                : request.type();

        validationPolicy.validate(desiredText, desiredType);

        String normalizedText = KeywordNormalizer.normalize(desiredText);
        validateDuplicate(
                userId,
                keywordId,
                normalizedText,
                desiredType
        );

        String fallbackCanonicalKey = keyword.desiredCanonicalKey() == null
                ? normalizedText
                : keyword.desiredCanonicalKey();
        String canonicalKey = validationPolicy.normalizeCanonicalKey(
                request.canonicalKey(),
                fallbackCanonicalKey
        );
        SystemKeyword parentKeyword = resolveParentKeyword(
                request.parentKeywordId()
        );
        hierarchyPolicy.validateCustomHierarchy(
                desiredType,
                parentKeyword
        );
        LocalDate effectiveDate = applicationTimingPolicy.resolveEffectiveDate(
                userId,
                today
        );

        keyword.scheduleUpdate(
                desiredText.trim(),
                normalizedText,
                desiredType,
                canonicalKey,
                parentKeyword,
                request.active(),
                effectiveDate
        );
        keyword.promoteIfEffective(today);

        return keyword;
    }

    public void deactivate(Long userId, Long keywordId) {
        CustomKeyword keyword = getOwnedKeyword(userId, keywordId);
        LocalDate today = keywordQueryService.resolveToday(userId);
        LocalDate effectiveDate = applicationTimingPolicy.resolveEffectiveDate(
                userId,
                today
        );

        keyword.promoteIfEffective(today);
        keyword.scheduleUpdate(
                keyword.getText(),
                keyword.getNormalizedText(),
                keyword.getType(),
                keyword.getCanonicalKey(),
                keyword.desiredParentSystemKeyword(),
                false,
                effectiveDate
        );
        keyword.promoteIfEffective(today);
    }

    private SystemKeyword resolveParentKeyword(Long parentKeywordId) {
        if (parentKeywordId == null) {
            return null;
        }
        return systemKeywordRepository.findById(parentKeywordId)
                .orElseThrow(hierarchyPolicy::invalidHierarchy);
    }

    private CustomKeyword getOwnedKeyword(
            Long userId,
            Long keywordId
    ) {
        return customKeywordRepository.findByIdAndUserId(
                keywordId,
                userId
        ).orElseThrow(validationPolicy::keywordNotFound);
    }

    private void validateDuplicate(
            Long userId,
            Long excludedKeywordId,
            String normalizedText,
            KeywordType type
    ) {
        boolean duplicated = customKeywordRepository
                .findAllByUserIdOrderByIdAsc(userId)
                .stream()
                .filter(keyword -> excludedKeywordId == null
                        || !excludedKeywordId.equals(keyword.getId()))
                .anyMatch(keyword -> normalizedText.equals(
                        keyword.desiredNormalizedText()
                ) && type == keyword.desiredType());

        if (duplicated) {
            throw validationPolicy.duplicateKeyword();
        }
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        "사용자를 찾을 수 없습니다.",
                        LanguageLearningErrorCode.USER_NOT_FOUND
                ));
    }
}
