package jp.co.translacat.domain.novel.genre.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jp.co.translacat.domain.novel.genre.entity.Genre;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GenreResponseDto {
    @Schema(description = "genre ID", example = "1")
    private Long id;

    @Schema(description = "장르 식별 ID", example = "_101")
    private String identifier;

    @Schema(description = "장르의 일어명", example = "<ruby>異世界<rt>いせかい</rt></ruby>〔<ruby>恋愛<rt>れんあい</rt></ruby>〕")
    private String nameJa;

    @Schema(description = "장르의 한글명", example = "이세계〔연애〕")
    private String nameKo;

    public static GenreResponseDto of(Genre genre) {
        return GenreResponseDto.builder()
                .id(genre.getId())
                .identifier(genre.getIdentifier())
                .nameJa(genre.getNameJa())
                .nameKo(genre.getNameKo())
                .build();
    }
}
