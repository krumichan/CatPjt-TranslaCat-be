package jp.co.translacat.domain.accountbook.analysis.service;

import jp.co.translacat.domain.accountbook.analysis.dto.SpendingAnalysisDtos.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;

class SpendingAnalysisPeriodsTest {
    @Test
    void quarterAndHalfHaveExactCalendarBounds() {
        // 준비 / 실행
        var quarter = SpendingAnalysisPeriods.resolve(new Request(PeriodType.QUARTERLY,2024,null,"ko",null,1,null,null,null));
        var half = SpendingAnalysisPeriods.resolve(new Request(PeriodType.HALF_YEARLY,2026,null,"ko",null,null,2,null,null));

        // 검증: 윤일과 연도 경계를 포함한다.
        assertThat(quarter.start()).isEqualTo(LocalDate.of(2024,1,1));
        assertThat(quarter.end()).isEqualTo(LocalDate.of(2024,4,1));
        assertThat(half.start()).isEqualTo(LocalDate.of(2026,7,1));
        assertThat(half.end()).isEqualTo(LocalDate.of(2027,1,1));
    }

    @Test
    void customDatesIncludeLastDayAndClampMonthEnd() {
        // 준비
        var accepted = new Request(PeriodType.CUSTOM,2026,null,"ko",null,null,null,LocalDate.of(2026,1,31),LocalDate.of(2026,2,27));
        var tooShort = new Request(PeriodType.CUSTOM,2026,null,"ko",null,null,null,LocalDate.of(2026,1,31),LocalDate.of(2026,2,26));

        // 실행 / 검증
        assertThat(SpendingAnalysisPeriods.resolve(accepted).end()).isEqualTo(LocalDate.of(2026,2,28));
        assertThatThrownBy(() -> SpendingAnalysisPeriods.resolve(tooShort)).isInstanceOf(IllegalArgumentException.class);
        assertThat(tooShort.isValidPeriod()).isFalse();
    }
}
