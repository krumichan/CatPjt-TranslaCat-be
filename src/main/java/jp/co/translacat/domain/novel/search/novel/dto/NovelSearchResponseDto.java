package jp.co.translacat.domain.novel.search.novel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jp.co.translacat.domain.novel.novel.model.NovelContext;
import jp.co.translacat.domain.novel.translation.model.TranslationUnit;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NovelSearchResponseDto {
    private String identifier;

    @JsonProperty("isShortStory")
    private boolean isShortStory;

    private TranslationUnit title;
    private TranslationUnit author;
    private TranslationUnit status;
    private TranslationUnit synopsis;

    public static NovelSearchResponseDto of(NovelContext novelContext) {
        NovelSearchResponseDto response = new NovelSearchResponseDto();
        response.identifier = novelContext.getIdentifier();
        response.isShortStory = novelContext.isShortStory();
        response.title = novelContext.getTitle();
        response.author = novelContext.getAuthor();
        response.status = novelContext.getStatus();
        response.synopsis = novelContext.getSynopsis();
        return response;
    }
}
