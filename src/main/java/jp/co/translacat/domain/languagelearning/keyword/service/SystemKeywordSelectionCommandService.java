package jp.co.translacat.domain.languagelearning.keyword.service;

import jp.co.translacat.domain.languagelearning.keyword.entity.SystemKeyword;
import jp.co.translacat.domain.languagelearning.keyword.entity.UserSystemKeywordSelection;
import jp.co.translacat.domain.languagelearning.keyword.policy.KeywordValidationPolicy;
import jp.co.translacat.domain.languagelearning.keyword.repository.SystemKeywordRepository;
import jp.co.translacat.domain.languagelearning.keyword.repository.UserSystemKeywordSelectionRepository;
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
public class SystemKeywordSelectionCommandService {

    private final SystemKeywordRepository systemKeywordRepository;
    private final UserSystemKeywordSelectionRepository selectionRepository;
    private final UserRepository userRepository;
    private final LanguageLearningKeywordQueryService keywordQueryService;
    private final KeywordValidationPolicy validationPolicy;

    public UserSystemKeywordSelection updateSelection(
            Long userId,
            Long keywordId,
            boolean selected
    ) {
        User user = getUser(userId);
        SystemKeyword keyword = systemKeywordRepository.findById(keywordId)
                .filter(SystemKeyword::isActive)
                .orElseThrow(validationPolicy::keywordNotFound);
        LocalDate today = keywordQueryService.resolveToday(userId);

        UserSystemKeywordSelection selection = selectionRepository
                .findByUserIdAndSystemKeywordId(userId, keywordId)
                .orElseGet(() -> createSelection(
                        user,
                        keyword,
                        today.plusDays(1)
                ));

        selection.promoteIfEffective(today);
        selection.scheduleActive(
                selected,
                today.plusDays(1)
        );

        return selection;
    }

    private UserSystemKeywordSelection createSelection(
            User user,
            SystemKeyword keyword,
            LocalDate effectiveDate
    ) {
        return selectionRepository.save(
                UserSystemKeywordSelection.create(
                        user,
                        keyword,
                        effectiveDate
                )
        );
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        "사용자를 찾을 수 없습니다.",
                        LanguageLearningErrorCode.USER_NOT_FOUND
                ));
    }
}
