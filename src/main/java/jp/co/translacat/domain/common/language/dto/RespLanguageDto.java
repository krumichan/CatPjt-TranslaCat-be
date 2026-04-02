package jp.co.translacat.domain.common.language.dto;

import jp.co.translacat.domain.common.language.entity.Language;
import lombok.Getter;

@Getter
public class RespLanguageDto {
    private String code;
    private String name;

    public static RespLanguageDto of(Language language) {
        RespLanguageDto dto = new RespLanguageDto();

        dto.code = language.getCode();
        dto.name = language.getName();

        return dto;
    }
}
