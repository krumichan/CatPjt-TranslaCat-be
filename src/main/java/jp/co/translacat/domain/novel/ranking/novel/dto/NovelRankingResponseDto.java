package jp.co.translacat.domain.novel.ranking.novel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jp.co.translacat.domain.novel.novel.model.NovelContext;
import jp.co.translacat.domain.novel.translation.model.TranslationUnit;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NovelRankingResponseDto {
    private int rank;

    private String identifier;

    @JsonProperty("isShortStory")
    private boolean isShortStory;

    private TranslationUnit title;
    private TranslationUnit status;
    private TranslationUnit synopsis;

    public static NovelRankingResponseDto of(NovelContext novelRank) {
        NovelRankingResponseDto result = new NovelRankingResponseDto();
        result.rank = novelRank.getRank();
        result.identifier = novelRank.getIdentifier();
        result.title = novelRank.getTitle();
        result.status = novelRank.getStatus();
        result.synopsis = novelRank.getSynopsis();
        result.isShortStory = novelRank.isShortStory();
        return result;
    }
}
