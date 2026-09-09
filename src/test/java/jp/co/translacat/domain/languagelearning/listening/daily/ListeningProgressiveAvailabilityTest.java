package jp.co.translacat.domain.languagelearning.listening.daily;

import jp.co.translacat.domain.languagelearning.listening.common.enums.*;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.ListeningDailySet;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

class ListeningProgressiveAvailabilityTest {
    private ListeningDailySet set() {
        return ListeningDailySet.create(null, LocalDate.now(), "ko", "ja",
                ListeningLearningMode.DICTATION, ListeningDifficulty.MY_LEVEL,
                "{}", "[]", "{}", "policy", 5);
    }

    @Test
    void firstFailureIsFailedButPartialFailurePreservesGeneratedItems() {
        var empty = set();
        empty.fail("first item");
        assertThat(empty.getStatus()).isEqualTo(ListeningDailySetStatus.FAILED);
        var partial = set();
        partial.recordPhysicalItem();
        partial.recordPhysicalItem();
        partial.fail("third item");
        assertThat(partial.getStatus()).isEqualTo(ListeningDailySetStatus.PARTIAL);
        assertThat(partial.getPhysicalItemCount()).isEqualTo(2);
    }

    @Test
    void audioPendingWithNoReadyItemsDoesNotFailSet() {
        var set = set();
        set.recordPhysicalItem();
        set.refreshAvailability(0, 1);
        assertThat(set.getStatus()).isEqualTo(ListeningDailySetStatus.PARTIAL);
        assertThat(set.getFailureReason()).isNull();
    }

    @Test
    void audioSuccessAndReplacementDoNotEraseLaterGenerationFailure() {
        var set = set();
        set.recordPhysicalItem();
        set.fail("third item failed");
        set.generated("generator");
        set.refreshAvailability(1, 0);
        assertThat(set.getStatus()).isEqualTo(ListeningDailySetStatus.PARTIAL);
        assertThat(set.getFailureReason()).isEqualTo("third item failed");
    }

    @Test
    void retryKeepsPhysicalItemsAndVersion() {
        var set = set();
        set.recordPhysicalItem();
        set.generated("generator");
        set.fail("second item failed");
        set.restartGeneration();
        assertThat(set.getPhysicalItemCount()).isEqualTo(1);
        assertThat(set.getGenerationVersion()).isEqualTo("generator");
        assertThat(set.getFailureReason()).isNull();
        assertThat(set.getStatus()).isEqualTo(ListeningDailySetStatus.PARTIAL);
    }

    @Test
    void lateCallbacksNeverReopenCompletedSet() {
        var set = set();
        for (int index = 0; index < 5; index++) set.registerCompletedLearning();
        set.refreshAvailability(4, 1);
        set.fail("late callback");
        assertThat(set.getStatus()).isEqualTo(ListeningDailySetStatus.COMPLETED);
        assertThat(set.getFailureReason()).isNull();
    }
}
