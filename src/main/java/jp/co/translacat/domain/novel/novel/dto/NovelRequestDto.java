package jp.co.translacat.domain.novel.novel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jp.co.translacat.global.dto.PageableDto;
import lombok.Getter;

@Getter
public class NovelRequestDto extends PageableDto {
    @Schema(description = "소설 식별 ID", defaultValue = "n7787eq")
    private String identifier;
}
