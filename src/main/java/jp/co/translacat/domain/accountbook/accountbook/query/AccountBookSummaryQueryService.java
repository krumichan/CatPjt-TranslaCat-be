package jp.co.translacat.domain.accountbook.accountbook.query;

import jp.co.translacat.domain.accountbook.accountbook.dto.AccountBookSummaryResponseDto;
import jp.co.translacat.domain.accountbook.accountbook.repository.AccountBookRepository;
import jp.co.translacat.domain.accountbook.accountbook.repository.AccountBookSummaryRepositoryCustom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountBookSummaryQueryService {

    private final AccountBookRepository accountBookRepository;
    private final AccountBookSummaryRepositoryCustom accountBookSummaryRepository;

    public AccountBookSummaryResponseDto getSummary(
            Long accountBookId,
            Integer year,
            Integer month
    ) {
        accountBookRepository.findById(accountBookId)
                .orElseThrow(() -> new IllegalArgumentException("Account book not found."));

        return accountBookSummaryRepository.getSummary(
                accountBookId,
                year,
                month
        );
    }
}