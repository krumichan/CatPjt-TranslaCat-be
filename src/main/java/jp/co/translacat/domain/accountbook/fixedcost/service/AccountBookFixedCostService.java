package jp.co.translacat.domain.accountbook.fixedcost.service;

import jp.co.translacat.domain.accountbook.accountbook.entity.AccountBook;
import jp.co.translacat.domain.accountbook.accountbook.service.AccountBookAccessService;
import jp.co.translacat.domain.accountbook.category.service.AccountBookCategoryService;
import jp.co.translacat.domain.accountbook.fixedcost.dto.AccountBookFixedCostGenerateRequestDto;
import jp.co.translacat.domain.accountbook.fixedcost.dto.AccountBookFixedCostGenerateResponseDto;
import jp.co.translacat.domain.accountbook.fixedcost.dto.AccountBookFixedCostGenerationTargetResponseDto;
import jp.co.translacat.domain.accountbook.fixedcost.dto.AccountBookFixedCostGenerationTargetsResponseDto;
import jp.co.translacat.domain.accountbook.fixedcost.dto.AccountBookFixedCostRequestDto;
import jp.co.translacat.domain.accountbook.fixedcost.dto.AccountBookFixedCostResponseDto;
import jp.co.translacat.domain.accountbook.fixedcost.entity.AccountBookFixedCost;
import jp.co.translacat.domain.accountbook.fixedcost.repository.AccountBookFixedCostRepository;
import jp.co.translacat.domain.accountbook.transaction.entity.AccountBookTransaction;
import jp.co.translacat.domain.accountbook.transaction.enums.AccountBookTransactionSourceType;
import jp.co.translacat.domain.accountbook.transaction.repository.AccountBookTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountBookFixedCostService {

    private final AccountBookAccessService accountBookAccessService;
    private final AccountBookFixedCostRepository accountBookFixedCostRepository;
    private final AccountBookCategoryService accountBookCategoryService;
    private final AccountBookTransactionRepository accountBookTransactionRepository;

    public List<AccountBookFixedCostResponseDto> getFixedCosts(
            Long accountBookId,
            Long userId
    ) {
        accountBookAccessService.validateAccessible(accountBookId, userId);

        return accountBookFixedCostRepository
                .findByAccountBookIdAndDeletedFalseOrderByActiveDescPaymentDayAscIdDesc(accountBookId)
                .stream()
                .map(AccountBookFixedCostResponseDto::from)
                .toList();
    }

    @Transactional
    public AccountBookFixedCostResponseDto createFixedCost(
            Long accountBookId,
            AccountBookFixedCostRequestDto request,
            Long userId
    ) {
        AccountBook accountBook = accountBookAccessService.getAccessibleAccountBook(
                accountBookId,
                userId
        );

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
            AccountBookFixedCostRequestDto request,
            Long userId
    ) {
        accountBookAccessService.validateAccessible(accountBookId, userId);

        validateEndMonth(request);

        AccountBookFixedCost fixedCost = getFixedCost(
                accountBookId,
                fixedCostId
        );

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
            Boolean active,
            Long userId
    ) {
        accountBookAccessService.validateAccessible(accountBookId, userId);

        AccountBookFixedCost fixedCost = getFixedCost(
                accountBookId,
                fixedCostId
        );

        if (Boolean.TRUE.equals(active)) {
            fixedCost.activate();
        } else {
            fixedCost.deactivate();
        }

        return AccountBookFixedCostResponseDto.from(fixedCost);
    }

    @Transactional
    public boolean deleteFixedCost(
            Long accountBookId,
            Long fixedCostId,
            Long userId
    ) {
        accountBookAccessService.validateAccessible(accountBookId, userId);

        AccountBookFixedCost fixedCost = getFixedCost(
                accountBookId,
                fixedCostId
        );

        fixedCost.delete();

        return true;
    }

    public AccountBookFixedCostGenerationTargetsResponseDto getGenerationTargets(
            Long accountBookId,
            Integer year,
            Integer month,
            Long userId
    ) {
        accountBookAccessService.validateAccessible(accountBookId, userId);

        YearMonth targetMonth = toYearMonth(year, month);

        List<AccountBookFixedCostGenerationTargetResponseDto> targets =
                getGeneratableFixedCosts(accountBookId, targetMonth)
                        .stream()
                        .map(fixedCost -> toGenerationTarget(fixedCost, targetMonth))
                        .toList();

        return AccountBookFixedCostGenerationTargetsResponseDto.of(
                year,
                month,
                targets
        );
    }

    @Transactional
    public AccountBookFixedCostGenerateResponseDto generateTransactions(
            Long accountBookId,
            AccountBookFixedCostGenerateRequestDto request,
            Long userId
    ) {
        AccountBook accountBook = accountBookAccessService.getAccessibleAccountBook(
                accountBookId,
                userId
        );

        YearMonth targetMonth = toYearMonth(
                request.year(),
                request.month()
        );

        List<AccountBookFixedCost> generatableFixedCosts =
                getGeneratableFixedCosts(accountBookId, targetMonth);

        List<AccountBookTransaction> transactions = generatableFixedCosts
                .stream()
                .map(fixedCost -> toTransaction(accountBook, fixedCost, targetMonth))
                .toList();

        accountBookTransactionRepository.saveAll(transactions);

        LocalDate generatedMonth = targetMonth.atDay(1);

        generatableFixedCosts.forEach(fixedCost ->
                updateLastGeneratedMonthIfAfter(fixedCost, generatedMonth)
        );

        return new AccountBookFixedCostGenerateResponseDto(
                request.year(),
                request.month(),
                transactions.size()
        );
    }

    @Transactional
    public AccountBookFixedCostGenerateResponseDto generateTransactionsForBatch(
            Long accountBookId,
            AccountBookFixedCostGenerateRequestDto request
    ) {
        AccountBook accountBook = accountBookAccessService.getActiveAccountBook(accountBookId);

        return generateTransactionsInternal(accountBook, request);
    }

    private List<AccountBookFixedCost> getGeneratableFixedCosts(
            Long accountBookId,
            YearMonth targetMonth
    ) {
        return accountBookFixedCostRepository
                .findByAccountBookIdAndActiveTrueAndDeletedFalseOrderByPaymentDayAscIdDesc(accountBookId)
                .stream()
                .filter(fixedCost -> isTargetMonthInRange(fixedCost, targetMonth))
                .filter(fixedCost -> !isAlreadyGenerated(
                        accountBookId,
                        fixedCost.getId(),
                        targetMonth.getYear(),
                        targetMonth.getMonthValue()
                ))
                .toList();
    }

    private AccountBookFixedCostGenerationTargetResponseDto toGenerationTarget(
            AccountBookFixedCost fixedCost,
            YearMonth targetMonth
    ) {
        return new AccountBookFixedCostGenerationTargetResponseDto(
                fixedCost.getId(),
                fixedCost.getTitle(),
                fixedCost.getStoreName(),
                fixedCost.getCategory(),
                fixedCost.getAmount(),
                fixedCost.getPaymentDay(),
                toTransactionDate(targetMonth, fixedCost.getPaymentDay()),
                fixedCost.getMemo()
        );
    }

    private AccountBookTransaction toTransaction(
            AccountBook accountBook,
            AccountBookFixedCost fixedCost,
            YearMonth targetMonth
    ) {
        return AccountBookTransaction.createFromFixedCost(
                accountBook,
                fixedCost.getTitle(),
                fixedCost.getStoreName(),
                fixedCost.getCategory(),
                fixedCost.getAmount(),
                toTransactionDate(targetMonth, fixedCost.getPaymentDay()),
                fixedCost.getMemo(),
                fixedCost.getId(),
                targetMonth.getYear(),
                targetMonth.getMonthValue()
        );
    }

    private void updateLastGeneratedMonthIfAfter(
            AccountBookFixedCost fixedCost,
            LocalDate generatedMonth
    ) {
        if (
                fixedCost.getLastGeneratedMonth() == null ||
                        fixedCost.getLastGeneratedMonth().isBefore(generatedMonth)
        ) {
            fixedCost.updateLastGeneratedMonth(generatedMonth);
        }
    }

    private AccountBookFixedCost getFixedCost(
            Long accountBookId,
            Long fixedCostId
    ) {
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

    private YearMonth toYearMonth(Integer year, Integer month) {
        if (year == null || month == null) {
            throw new IllegalArgumentException("Year and month are required.");
        }

        try {
            return YearMonth.of(year, month);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Invalid year or month.", e);
        }
    }

    private void validateEndMonth(AccountBookFixedCostRequestDto request) {
        LocalDate startMonth = toMonthDate(
                request.startYear(),
                request.startMonth()
        );

        LocalDate endMonth = toNullableMonthDate(
                request.endYear(),
                request.endMonth()
        );

        if (endMonth != null && endMonth.isBefore(startMonth)) {
            throw new IllegalArgumentException("End month must be after start month.");
        }
    }

    private boolean isTargetMonthInRange(
            AccountBookFixedCost fixedCost,
            YearMonth targetMonth
    ) {
        YearMonth startMonth = YearMonth.from(fixedCost.getStartMonth());

        if (targetMonth.isBefore(startMonth)) {
            return false;
        }

        if (fixedCost.getEndMonth() == null) {
            return true;
        }

        YearMonth endMonth = YearMonth.from(fixedCost.getEndMonth());

        return !targetMonth.isAfter(endMonth);
    }

    private LocalDate toTransactionDate(
            YearMonth yearMonth,
            Integer paymentDay
    ) {
        int day = Math.min(paymentDay, yearMonth.lengthOfMonth());
        return yearMonth.atDay(day);
    }

    private boolean isAlreadyGenerated(
            Long accountBookId,
            Long fixedCostId,
            Integer year,
            Integer month
    ) {
        return accountBookTransactionRepository
                .existsByAccountBookIdAndSourceTypeAndSourceIdAndSourceYearAndSourceMonth(
                        accountBookId,
                        AccountBookTransactionSourceType.FIXED_COST,
                        fixedCostId,
                        year,
                        month
                );
    }

    private AccountBookFixedCostGenerateResponseDto generateTransactionsInternal(
            AccountBook accountBook,
            AccountBookFixedCostGenerateRequestDto request
    ) {
        Long accountBookId = accountBook.getId();

        YearMonth targetMonth = toYearMonth(
                request.year(),
                request.month()
        );

        List<AccountBookFixedCost> generatableFixedCosts =
                getGeneratableFixedCosts(accountBookId, targetMonth);

        List<AccountBookTransaction> transactions = generatableFixedCosts
                .stream()
                .map(fixedCost -> toTransaction(accountBook, fixedCost, targetMonth))
                .toList();

        accountBookTransactionRepository.saveAll(transactions);

        LocalDate generatedMonth = targetMonth.atDay(1);

        generatableFixedCosts.forEach(fixedCost ->
                updateLastGeneratedMonthIfAfter(fixedCost, generatedMonth)
        );

        return new AccountBookFixedCostGenerateResponseDto(
                request.year(),
                request.month(),
                transactions.size()
        );
    }
}