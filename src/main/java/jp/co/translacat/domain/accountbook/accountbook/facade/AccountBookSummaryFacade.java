package jp.co.translacat.domain.accountbook.accountbook.facade;

import jp.co.translacat.domain.accountbook.accountbook.dto.AccountBookSummaryResponseDto;
import jp.co.translacat.domain.accountbook.accountbook.query.AccountBookSummaryQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountBookSummaryFacade {

    private final AccountBookSummaryQueryService accountBookSummaryQueryService;

    public AccountBookSummaryResponseDto getSummary(
            Long accountBookId,
            Integer year,
            Integer month
    ) {
        return accountBookSummaryQueryService.getSummary(
                accountBookId,
                year,
                month
        );
    }
}