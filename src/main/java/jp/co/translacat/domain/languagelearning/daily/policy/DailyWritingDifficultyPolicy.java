package jp.co.translacat.domain.languagelearning.daily.policy;

import jp.co.translacat.domain.languagelearning.ai.dto.model.DifficultyDistributionDto;

import org.springframework.stereotype.Component;

@Component
public class DailyWritingDifficultyPolicy {

    public DifficultyDistributionDto distribute(int sentenceCount) {
        if (sentenceCount <= 0) {
            return new DifficultyDistributionDto(0, 0, 0);
        }

        if (sentenceCount < 3) {
            return new DifficultyDistributionDto(0, sentenceCount, 0);
        }

        int review = Math.max(
                1,
                (int) Math.round(sentenceCount * 0.20)
        );
        int challenge = Math.max(
                1,
                (int) Math.round(sentenceCount * 0.20)
        );
        int normal = sentenceCount - review - challenge;

        while (normal < 1) {
            if (review >= challenge && review > 1) {
                review--;
            } else if (challenge > 1) {
                challenge--;
            } else {
                break;
            }

            normal = sentenceCount - review - challenge;
        }

        return new DifficultyDistributionDto(
                review,
                normal,
                challenge
        );
    }
}
