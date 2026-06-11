package jp.co.translacat.domain.accountbook.fixedcost.service;

import jp.co.translacat.domain.accountbook.accountbook.entity.AccountBook;
import jp.co.translacat.domain.accountbook.accountbook.repository.AccountBookRepository;
import jp.co.translacat.domain.accountbook.category.service.AccountBookCategoryService;
import jp.co.translacat.domain.accountbook.fixedcost.dto.AccountBookFixedCostRequestDto;
import jp.co.translacat.domain.accountbook.fixedcost.dto.AccountBookFixedCostResponseDto;
import jp.co.translacat.domain.accountbook.fixedcost.entity.AccountBookFixedCost;
import jp.co.translacat.domain.accountbook.fixedcost.repository.AccountBookFixedCostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountBookFixedCostService {

    private final AccountBookRepository accountBookRepository;
    private final AccountBookFixedCostRepository accountBookFixedCostRepository;
    private final AccountBookCategoryService accountBookCategoryService;

    public List<AccountBookFixedCostResponseDto> getFixedCosts(Long accountBookId) {
        validateAccountBook(accountBookId);

        return accountBookFixedCostRepository
                .findByAccountBookIdAndDeletedFalseOrderByActiveDescPaymentDayAscIdDesc(accountBookId)
                .stream()
                .map(AccountBookFixedCostResponseDto::from)
                .toList();
    }

    @Transactional
    public AccountBookFixedCostResponseDto createFixedCost(
            Long accountBookId,
            AccountBookFixedCostRequestDto request
    ) {
        AccountBook accountBook = getAccountBook(accountBookId);

        validateEndMonth(request);

        accountBookCategoryService.findOrCreateCategory(
                accountBookId,
                request.category()
        );

        AccountBookFixedCost fixedCost = AccountBookFixedCost.create(
                accountBook,
                request.title(),
                request.storeName(),
                request.category(),
                request.amount(),
                request.paymentDay(),
                toMonthDate(request.startYear(), request.startMonth()),
                toNullableMonthDate(request.endYear(), request.endMonth()),
                request.memo()
        );

        return AccountBookFixedCostResponseDto.from(
                accountBookFixedCostRepository.save(fixedCost)
        );
    }

    @Transactional
    public AccountBookFixedCostResponseDto updateFixedCost(
            Long accountBookId,
            Long fixedCostId,
            AccountBookFixedCostRequestDto request
    ) {
        validateEndMonth(request);

        AccountBookFixedCost fixedCost = getFixedCost(accountBookId, fixedCostId);

        accountBookCategoryService.findOrCreateCategory(
                accountBookId,
                request.category()
        );

        fixedCost.update(
                request.title(),
                request.storeName(),
                request.category(),
                request.amount(),
                request.paymentDay(),
                toMonthDate(request.startYear(), request.startMonth()),
                toNullableMonthDate(request.endYear(), request.endMonth()),
                request.memo()
        );

        return AccountBookFixedCostResponseDto.from(fixedCost);
    }

    @Transactional
    public AccountBookFixedCostResponseDto updateActive(
            Long accountBookId,
            Long fixedCostId,
            Boolean active
    ) {
        AccountBookFixedCost fixedCost = getFixedCost(accountBookId, fixedCostId);

        if (Boolean.TRUE.equals(active)) {
            fixedCost.activate();
        } else {
            fixedCost.deactivate();
        }

        return AccountBookFixedCostResponseDto.from(fixedCost);
    }

    @Transactional
    public void deleteFixedCost(Long accountBookId, Long fixedCostId) {
        AccountBookFixedCost fixedCost = getFixedCost(accountBookId, fixedCostId);
        fixedCost.delete();
    }

    private AccountBook getAccountBook(Long accountBookId) {
        return accountBookRepository.findById(accountBookId)
                .orElseThrow(() -> new IllegalArgumentException("Account book not found."));
    }

    private void validateAccountBook(Long accountBookId) {
        if (!accountBookRepository.existsById(accountBookId)) {
            throw new IllegalArgumentException("Account book not found.");
        }
    }

    private AccountBookFixedCost getFixedCost(Long accountBookId, Long fixedCostId) {
        return accountBookFixedCostRepository.findById(fixedCostId)
                .filter(fixedCost -> fixedCost.getAccountBook().getId().equals(accountBookId))
                .filter(fixedCost -> !Boolean.TRUE.equals(fixedCost.getDeleted()))
                .orElseThrow(() -> new IllegalArgumentException("Fixed cost not found."));
    }

    private LocalDate toMonthDate(Integer year, Integer month) {
        return LocalDate.of(year, month, 1);
    }

    private LocalDate toNullableMonthDate(Integer year, Integer month) {
        if (year == null && month == null) {
            return null;
        }

        if (year == null || month == null) {
            throw new IllegalArgumentException("End year and end month must be entered together.");
        }

        return LocalDate.of(year, month, 1);
    }

    private void validateEndMonth(AccountBookFixedCostRequestDto request) {
        LocalDate startMonth = toMonthDate(request.startYear(), request.startMonth());
        LocalDate endMonth = toNullableMonthDate(request.endYear(), request.endMonth());

        if (endMonth != null && endMonth.isBefore(startMonth)) {
            throw new IllegalArgumentException("End month must be after start month.");
        }
    }
}