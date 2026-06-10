package jp.co.translacat.domain.accountbook.accountbook.repository;

import jp.co.translacat.domain.accountbook.accountbook.dto.AccountBookSummaryResponseDto;

public interface AccountBookSummaryRepositoryCustom {

    AccountBookSummaryResponseDto getSummary(
            Long accountBookId,
            Integer year,
            Integer month
    );
}