package jp.co.translacat.domain.accountbook.category.service;

import jp.co.translacat.domain.accountbook.accountbook.entity.AccountBook;
import jp.co.translacat.domain.accountbook.accountbook.repository.AccountBookRepository;
import jp.co.translacat.domain.accountbook.accountbook.service.AccountBookAccessService;
import jp.co.translacat.domain.accountbook.category.dto.AccountBookCategoryRequestDto;
import jp.co.translacat.domain.accountbook.category.dto.AccountBookCategoryResponseDto;
import jp.co.translacat.domain.accountbook.category.entity.AccountBookCategory;
import jp.co.translacat.domain.accountbook.category.repository.AccountBookCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountBookCategoryService {

    private final AccountBookRepository accountBookRepository;
    private final AccountBookCategoryRepository accountBookCategoryRepository;

    private final AccountBookAccessService accountBookAccessService;

    public List<AccountBookCategoryResponseDto> getCategories(
            Long accountBookId,
            Long userId
    ) {
        accountBookAccessService.validateAccessible(accountBookId, userId);

        return accountBookCategoryRepository
                .findByAccountBookIdAndActiveTrueOrderByDisplayOrderAscNameAsc(accountBookId)
                .stream()
                .map(AccountBookCategoryResponseDto::from)
                .toList();
    }

    @Transactional
    public AccountBookCategoryResponseDto createCategory(
            Long accountBookId,
            AccountBookCategoryRequestDto request,
            Long userId
    ) {
        AccountBook accountBook = accountBookAccessService
                .getAccessibleAccountBook(accountBookId, userId);

        String name = normalizeName(request.name());

        accountBookCategoryRepository
                .findByAccountBookIdAndName(accountBookId, name)
                .ifPresent(category -> {
                    throw new IllegalArgumentException("Category already exists.");
                });

        Integer nextDisplayOrder = getNextDisplayOrder(accountBookId);

        AccountBookCategory category = AccountBookCategory.create(
                accountBook,
                name,
                nextDisplayOrder
        );

        return AccountBookCategoryResponseDto.from(
                accountBookCategoryRepository.save(category)
        );
    }

    @Transactional
    public AccountBookCategory findOrCreateCategory(
            Long accountBookId,
            String categoryName
    ) {
        AccountBook accountBook = getAccountBook(accountBookId);
        String normalizedName = normalizeName(categoryName);

        return accountBookCategoryRepository
                .findByAccountBookIdAndName(accountBookId, normalizedName)
                .orElseGet(() -> accountBookCategoryRepository.save(
                        AccountBookCategory.create(
                                accountBook,
                                normalizedName,
                                getNextDisplayOrder(accountBookId)
                        )
                ));
    }

    private AccountBook getAccountBook(Long accountBookId) {
        return accountBookRepository.findById(accountBookId)
                .orElseThrow(() -> new IllegalArgumentException("Account book not found."));
    }

    private Integer getNextDisplayOrder(Long accountBookId) {
        return accountBookCategoryRepository
                .findByAccountBookIdAndActiveTrueOrderByDisplayOrderAscNameAsc(accountBookId)
                .stream()
                .map(AccountBookCategory::getDisplayOrder)
                .max(Integer::compareTo)
                .map(order -> order + 1)
                .orElse(1);
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Category name is required.");
        }

        return name.trim();
    }
}