package jp.co.translacat.domain.languagelearning.practice.policy;

import jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain;
import jp.co.translacat.domain.languagelearning.practice.repository.PracticeSetRepository;
import jp.co.translacat.domain.languagelearning.profile.repository.LearningProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class PracticeComplexityPolicyTest {
    @Mock
    LearningProfileRepository profileRepository;
    @Mock
    PracticeSetRepository practiceSetRepository;

    PracticeComplexityPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new PracticeComplexityPolicy(profileRepository, practiceSetRepository);
    }

    @Test
    void mapsInternalScoreToFiveComplexityBandsWithoutExternalExamLabels() {
        assertThat(policy.baseBand(null)).isEqualTo(3);
        assertThat(policy.baseBand(39.99)).isEqualTo(1);
        assertThat(policy.baseBand(40.0)).isEqualTo(2);
        assertThat(policy.baseBand(55.0)).isEqualTo(3);
        assertThat(policy.baseBand(70.0)).isEqualTo(4);
        assertThat(policy.baseBand(85.0)).isEqualTo(5);
    }

    @Test
    void usesReadingAndVocabularyDifficultyMixes() {
        assertThat(policy.mix(PracticeDomain.READING)).containsExactly(1, 3, 1);
        assertThat(policy.mix(PracticeDomain.VOCABULARY)).containsExactly(2, 6, 2);
    }
}
