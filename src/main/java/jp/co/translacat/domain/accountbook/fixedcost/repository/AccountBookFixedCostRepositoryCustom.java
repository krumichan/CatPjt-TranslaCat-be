package jp.co.translacat.domain.accountbook.fixedcost.repository;

import java.time.LocalDate;
import java.util.List;

public interface AccountBookFixedCostRepositoryCustom {

    List<Long> findGenerationTargetAccountBookIds(LocalDate targetMonth);
}