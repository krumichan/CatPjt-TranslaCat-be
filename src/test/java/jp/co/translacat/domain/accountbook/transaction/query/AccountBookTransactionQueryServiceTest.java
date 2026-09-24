package jp.co.translacat.domain.accountbook.transaction.query;

import jp.co.translacat.domain.accountbook.accountbook.service.AccountBookAccessService;
import jp.co.translacat.domain.accountbook.transaction.enums.AccountBookTransactionType;
import jp.co.translacat.domain.accountbook.transaction.repository.AccountBookTransactionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountBookTransactionQueryServiceTest {
    @Test
    void storeSuggestionsAreScopedByTransactionTypeAfterAccessValidation() {
        var access = mock(AccountBookAccessService.class);
        var repository = mock(AccountBookTransactionRepository.class);
        var service = new AccountBookTransactionQueryService(access, repository);
        when(repository.findStoreSuggestions(1L, AccountBookTransactionType.INCOME, "salary"))
                .thenReturn(List.of());

        assertThat(service.getStoreSuggestions(
                1L, AccountBookTransactionType.INCOME, "salary", 2L)).isEmpty();

        verify(access).validateAccessible(1L, 2L);
        verify(repository).findStoreSuggestions(
                1L, AccountBookTransactionType.INCOME, "salary");
    }
}
