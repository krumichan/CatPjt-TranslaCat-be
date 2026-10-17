package jp.co.translacat.domain.common.language.dto;

import jp.co.translacat.domain.common.language.entity.Language;
import lombok.Getter;

@Getter
public class RespLanguageDto {
    private String code;
    private String name;

    public static RespLanguageDto of(Language language) {
        return of(language.getCode(), language.getName());
    }

    public static RespLanguageDto of(String code, String name) {
        RespLanguageDto dto = new RespLanguageDto();

        dto.code = code;
        dto.name = name;

        return dto;
    }
}
